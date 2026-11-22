package com.chubb.claims.events;

import com.chubb.claims.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end through the outbox, Kafka and both consumers.
 *
 * NOT rolled back like the other integration tests: the relay only sees committed outbox rows.
 * Each test creates its own claim and deletes everything about it afterwards, so the seed data other
 * test classes rely on is unchanged.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClaimEventsIntegrationTest extends IntegrationTestBase {

    private static final String ALICE_SG_MOTOR = "00000000-0000-0000-0000-000000000101";
    private static final String PRIYAS_UNDER_REVIEW = "00000000-0000-0000-0000-000000001002";
    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    KafkaTemplate<String, String> kafka;

    /** Claim created by the current test, removed in cleanUp(). */
    private String claimId;

    @AfterEach
    void cleanUp() {
        if (claimId == null) return;
        for (String table : List.of("notification", "claim_event_log", "information_request", "claim_status_history")) {
            jdbc.update("DELETE FROM " + table + " WHERE claim_id = ?::uuid", claimId);
        }
        jdbc.update("DELETE FROM outbox_event WHERE aggregate_id = ?::uuid", claimId);
        jdbc.update("DELETE FROM claim WHERE id = ?::uuid", claimId);
    }

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class, claimId);
        return n == null ? 0 : n;
    }

    @Test
    void claimLifecycleFlowsThroughOutboxKafkaAuditLogAndNotifications() throws Exception {
        // Alice reports an incident
        MvcResult created = mvc.perform(post("/api/claims").header("X-User-Id", ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyId": "%s", "incidentDate": "%s", "incidentDescription": "Event flow test claim", "estimatedLoss": 2000}
                                """.formatted(ALICE_SG_MOTOR, LocalDate.of(2026, 11, 1))))
                .andExpect(status().isCreated()).andReturn();
        claimId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        // Priya picks up, revises the reserve and asks a question; Alice answers
        mvc.perform(post("/api/claims/{id}/pick-up", claimId).header("X-User-Id", PRIYA_STAFF)).andExpect(status().isOk());
        mvc.perform(put("/api/claims/{id}/reserve", claimId).header("X-User-Id", PRIYA_STAFF)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 1500}")).andExpect(status().isOk());
        mvc.perform(post("/api/claims/{id}/request-info", claimId).header("X-User-Id", PRIYA_STAFF)
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\": \"Please send the repair quote\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/claims/{id}/answer", claimId).header("X-User-Id", ALICE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"response\": \"Quote sent by email today\"}"))
                .andExpect(status().isOk());

        // Outbox: 5 events, written with the changes; the relay publishes all of them
        assertThat(count("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?::uuid")).isEqualTo(5);
        await().atMost(WAIT).until(() ->
                count("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?::uuid AND published_at IS NOT NULL") == 5);

        // Audit log: all 5 events, including the reserve revision with before/after
        await().atMost(WAIT).until(() -> count("SELECT count(*) FROM claim_event_log WHERE claim_id = ?::uuid") == 5);
        String reserveEvent = jdbc.queryForObject(
                "SELECT payload::text FROM claim_event_log WHERE claim_id = ?::uuid AND event_type = 'RESERVE_REVISED'",
                String.class, claimId);
        // Read fields rather than matching text: Postgres reformats stored JSON (spacing, number scale)
        assertThat(new BigDecimal(JsonPath.read(reserveEvent, "$.reserveBefore").toString()))
                .isEqualByComparingTo("2000");
        assertThat(new BigDecimal(JsonPath.read(reserveEvent, "$.reserveAfter").toString()))
                .isEqualByComparingTo("1500");
        assertThat((String) JsonPath.read(reserveEvent, "$.actorName")).isEqualTo("Priya Nair");

        // Notifications: only Priya's two status changes. Not Alice's own submit/answer, not the internal reserve change.
        await().atMost(WAIT).until(() -> count("SELECT count(*) FROM notification WHERE claim_id = ?::uuid") == 2);
        mvc.perform(get("/api/me/notifications").header("X-User-Id", ALICE))
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.items[0].message").value(startsWith("Question on CLM-")))
                .andExpect(jsonPath("$.items[1].message").value(startsWith("Priya Nair picked up CLM-")));

        // Duplicate delivery: re-sending an already-processed event changes nothing
        String payload = jdbc.queryForObject(
                "SELECT payload::text FROM outbox_event WHERE aggregate_id = ?::uuid AND event_type = 'CLAIM_STATUS_CHANGED' "
                        + "ORDER BY created_at LIMIT 1", String.class, claimId);
        kafka.send(KafkaTopics.CLAIM_EVENTS, claimId, payload).get();
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).until(() ->
                count("SELECT count(*) FROM claim_event_log WHERE claim_id = ?::uuid") == 5
                        && count("SELECT count(*) FROM notification WHERE claim_id = ?::uuid") == 2);
    }

    @Test
    void notificationsCanBeMarkedRead() throws Exception {
        // Reuse the flow's first steps: a staff pick-up creates one notification
        MvcResult created = mvc.perform(post("/api/claims").header("X-User-Id", ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyId": "%s", "incidentDate": "%s", "incidentDescription": "Mark read test claim", "estimatedLoss": 900}
                                """.formatted(ALICE_SG_MOTOR, LocalDate.of(2026, 11, 2))))
                .andExpect(status().isCreated()).andReturn();
        claimId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/claims/{id}/pick-up", claimId).header("X-User-Id", PRIYA_STAFF)).andExpect(status().isOk());
        await().atMost(WAIT).until(() -> count("SELECT count(*) FROM notification WHERE claim_id = ?::uuid") == 1);

        String notificationId = jdbc.queryForObject(
                "SELECT id::text FROM notification WHERE claim_id = ?::uuid", String.class, claimId);
        mvc.perform(post("/api/me/notifications/{id}/read", notificationId).header("X-User-Id", BEN))
                .andExpect(status().isNotFound());   // not Ben's
        mvc.perform(post("/api/me/notifications/{id}/read", notificationId).header("X-User-Id", ALICE))
                .andExpect(status().isNoContent());
        assertThat(count("SELECT count(*) FROM notification WHERE claim_id = ?::uuid AND read_at IS NOT NULL")).isEqualTo(1);
    }

    @Test
    void rejectedActionWritesNoEvent() throws Exception {
        // Daniel isn't assigned to Priya's claim: 403, and nothing reaches the outbox
        mvc.perform(post("/api/claims/{id}/approve", PRIYAS_UNDER_REVIEW).header("X-User-Id", DANIEL_STAFF)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 100}"))
                .andExpect(status().isForbidden());
        Integer events = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE aggregate_id = ?::uuid", Integer.class, PRIYAS_UNDER_REVIEW);
        assertThat(events).isZero();
    }

    @Test
    void claimantsSeeOnlyTheirOwnNotifications() throws Exception {
        mvc.perform(get("/api/me/notifications").header("X-User-Id", BEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/me/notifications").header("X-User-Id", PRIYA_STAFF))
                .andExpect(status().isForbidden());
    }
}
