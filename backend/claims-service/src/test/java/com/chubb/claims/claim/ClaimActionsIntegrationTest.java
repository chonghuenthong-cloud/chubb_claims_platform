package com.chubb.claims.claim;

import com.chubb.claims.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Staff actions and the claimant's answer, end to end against real Postgres. See IntegrationTestBase for the setup. */
class ClaimActionsIntegrationTest extends IntegrationTestBase {

    // Seeded claims (see db/seed/V100__seed_demo_data.sql)
    static final String SUBMITTED_UNASSIGNED = "00000000-0000-0000-0000-000000001001"; // Alice, SG motor, limit 50,000
    static final String PRIYAS_UNDER_REVIEW = "00000000-0000-0000-0000-000000001002";  // Ben, MY motor, limit 120,000
    static final String DANIELS_AWAITING_INFO = "00000000-0000-0000-0000-000000001003"; // Alice, open question
    static final String PRIYAS_APPROVED = "00000000-0000-0000-0000-000000001004";      // Ben

    /** POST or PUT to /api/claims/{id}/{action} as the given user, with an optional JSON body. */
    private ResultActions act(String userId, String claimId, String action, String json) throws Exception {
        var builder = (action.equals("reserve") ? put("/api/claims/{id}/reserve", claimId)
                : post("/api/claims/{id}/" + action, claimId))
                .header("X-User-Id", userId);
        if (json != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(builder);
    }

    // --- Pick up ---

    @Test
    void officerPicksUpUnassignedClaimAndSecondOfficerGetsConflict() throws Exception {
        act(PRIYA_STAFF, SUBMITTED_UNASSIGNED, "pick-up", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.summary.assignedOfficerId").value(PRIYA_STAFF))
                .andExpect(jsonPath("$.summary.assignedOfficerName").value("Priya Nair"));

        act(DANIEL_STAFF, SUBMITTED_UNASSIGNED, "pick-up", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Claim has already been picked up by Priya Nair"));
    }

    @Test
    void claimantCannotPickUp() throws Exception {
        act(ALICE, SUBMITTED_UNASSIGNED, "pick-up", null).andExpect(status().isForbidden());
    }

    // --- Happy path to settlement ---

    @Test
    void assignedOfficerRevisesReserveApprovesAndSettles() throws Exception {
        act(PRIYA_STAFF, PRIYAS_UNDER_REVIEW, "reserve", "{\"amount\": 10000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.reserveAmount").value(10000));

        // Approving sets the reserve to the approved amount
        act(PRIYA_STAFF, PRIYAS_UNDER_REVIEW, "approve", "{\"amount\": 9000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAmount").value(9000))
                .andExpect(jsonPath("$.summary.reserveAmount").value(9000));

        // Settling leaves nothing to pay
        act(PRIYA_STAFF, PRIYAS_UNDER_REVIEW, "settle", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("SETTLED"))
                .andExpect(jsonPath("$.summary.reserveAmount").value(0))
                .andExpect(jsonPath("$.history[-1].toStatus").value("SETTLED"))
                .andExpect(jsonPath("$.history[-1].changedByName").value("Priya Nair"));
    }

    // --- Ownership and status rules ---

    @Test
    void onlyTheAssignedOfficerCanAct() throws Exception {
        act(DANIEL_STAFF, PRIYAS_UNDER_REVIEW, "approve", "{\"amount\": 100}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only the assigned officer can do this"));
    }

    @Test
    void ownershipIsCheckedBeforeStatus() throws Exception {
        // Daniel isn't assigned AND the claim is already approved: ownership wins (403, not 409)
        act(DANIEL_STAFF, PRIYAS_APPROVED, "approve", "{\"amount\": 100}").andExpect(status().isForbidden());
    }

    @Test
    void actionThatDoesNotFitTheStatusIsConflict() throws Exception {
        act(PRIYA_STAFF, PRIYAS_APPROVED, "approve", "{\"amount\": 100}").andExpect(status().isConflict());
        act(PRIYA_STAFF, PRIYAS_APPROVED, "reserve", "{\"amount\": 100}").andExpect(status().isConflict());
    }

    @Test
    void amountsAboveCoverageLimitAreRejected() throws Exception {
        act(PRIYA_STAFF, PRIYAS_UNDER_REVIEW, "approve", "{\"amount\": 120000.01}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].message").value("Amount cannot exceed the coverage limit of MYR 120000.00"));
        act(PRIYA_STAFF, PRIYAS_UNDER_REVIEW, "reserve", "{\"amount\": 120000.01}")
                .andExpect(status().isBadRequest());
    }

    // --- Reject ---

    @Test
    void rejectRecordsReasonInClaimAndHistoryAndClearsReserve() throws Exception {
        String reason = "Damage is not consistent with the reported incident";
        act(PRIYA_STAFF, PRIYAS_UNDER_REVIEW, "reject", "{\"reason\": \"" + reason + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value(reason))
                .andExpect(jsonPath("$.summary.reserveAmount").value(0))
                .andExpect(jsonPath("$.history[-1].note").value(reason));
    }

    @Test
    void shortRejectionReasonIsRejected() throws Exception {
        act(PRIYA_STAFF, PRIYAS_UNDER_REVIEW, "reject", "{\"reason\": \"No\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
    }

    // --- Request info and answer ---

    @Test
    void officerRequestsInfoAndClaimantAnswers() throws Exception {
        String question = "Please send the workshop repair quote";
        act(PRIYA_STAFF, PRIYAS_UNDER_REVIEW, "request-info", "{\"question\": \"" + question + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("AWAITING_INFO"))
                .andExpect(jsonPath("$.informationRequests", hasSize(1)))
                .andExpect(jsonPath("$.informationRequests[0].question").value(question))
                .andExpect(jsonPath("$.history[-1].note").value(question));

        act(BEN, PRIYAS_UNDER_REVIEW, "answer", "{\"response\": \"Quote attached: MYR 8,400 from AutoFix\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.informationRequests[0].response").value("Quote attached: MYR 8,400 from AutoFix"))
                .andExpect(jsonPath("$.history[-1].changedByName").value("Ben Lim"))
                // The claimant gets the claimant view back: no reserve
                .andExpect(jsonPath("$.summary.reserveAmount").doesNotExist());
    }

    @Test
    void claimantAnswersSeededOpenQuestion() throws Exception {
        act(ALICE, DANIELS_AWAITING_INFO, "answer", "{\"response\": \"Plumber's report uploaded today\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("UNDER_REVIEW"));
    }

    @Test
    void answeringSomeoneElsesClaimIs404() throws Exception {
        act(BEN, DANIELS_AWAITING_INFO, "answer", "{\"response\": \"Not my claim at all\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void answeringWithoutAnOpenQuestionIsConflict() throws Exception {
        act(ALICE, SUBMITTED_UNASSIGNED, "answer", "{\"response\": \"Nobody asked me anything\"}")
                .andExpect(status().isConflict());
    }

    // --- Regression: rows actually written to the database ---

    @Autowired
    JdbcTemplate jdbc;

    /**
     * Regression test for a bug found in this milestone: saving an existing claim with saveAndFlush made JPA
     * "merge" it, and new history rows were written empty (claim_id null). The other tests check the API
     * response, which comes from memory; this one reads the tables directly.
     */
    @Test
    void actionsWriteCompleteHistoryAndInformationRequestRows() throws Exception {
        act(PRIYA_STAFF, PRIYAS_UNDER_REVIEW, "request-info", "{\"question\": \"Please send the workshop repair quote\"}")
                .andExpect(status().isOk());

        Map<String, Object> request = jdbc.queryForMap(
                "SELECT claim_id, requested_by, question FROM information_request WHERE claim_id = ?::uuid",
                PRIYAS_UNDER_REVIEW);
        assertThat(request.get("requested_by")).hasToString(PRIYA_STAFF);
        assertThat(request.get("question")).isEqualTo("Please send the workshop repair quote");

        Map<String, Object> latest = jdbc.queryForMap("""
                SELECT claim_id, from_status, to_status, changed_by, note FROM claim_status_history
                WHERE claim_id = ?::uuid ORDER BY changed_at DESC LIMIT 1""", PRIYAS_UNDER_REVIEW);
        assertThat(latest.get("claim_id")).hasToString(PRIYAS_UNDER_REVIEW);
        assertThat(latest.get("from_status")).isEqualTo("UNDER_REVIEW");
        assertThat(latest.get("to_status")).isEqualTo("AWAITING_INFO");
        assertThat(latest.get("changed_by")).hasToString(PRIYA_STAFF);
        assertThat(latest.get("note")).isEqualTo("Please send the workshop repair quote");

        // No incomplete rows anywhere in the history table
        Integer emptyRows = jdbc.queryForObject(
                "SELECT count(*) FROM claim_status_history WHERE claim_id IS NULL OR to_status IS NULL OR changed_by IS NULL",
                Integer.class);
        assertThat(emptyRows).isZero();
    }

    // --- Queue filters ---

    @Test
    void queueFiltersByAssignment() throws Exception {
        mvc.perform(get("/api/claims").param("filter", "UNASSIGNED").header("X-User-Id", PRIYA_STAFF))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].claimNumber").value("CLM-000001"));
        mvc.perform(get("/api/claims").param("filter", "MINE").header("X-User-Id", PRIYA_STAFF))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/claims").header("X-User-Id", PRIYA_STAFF))
                .andExpect(jsonPath("$", hasSize(5)));
    }
}
