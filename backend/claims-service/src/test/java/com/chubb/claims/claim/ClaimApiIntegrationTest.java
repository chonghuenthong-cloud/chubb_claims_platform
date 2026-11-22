package com.chubb.claims.claim;

import com.chubb.claims.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reporting an incident, listing claims and viewing one claim, end to end. See IntegrationTestBase for the setup. */
class ClaimApiIntegrationTest extends IntegrationTestBase {

    // Seeded policies
    static final String ALICE_SG_MOTOR = "00000000-0000-0000-0000-000000000101"; // limit 50,000 SGD, from 2026-01-01
    static final String BEN_MY_MOTOR = "00000000-0000-0000-0000-000000000103";

    private ResultActions submit(String userId, String policyId, LocalDate date, String description, String amount)
            throws Exception {
        String body = """
                {"policyId": "%s", "incidentDate": "%s", "incidentDescription": "%s", "estimatedLoss": %s}
                """.formatted(policyId, date, description, amount);
        return mvc.perform(post("/api/claims")
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void claimantReportsIncidentAndSeesItInBothLists() throws Exception {
        // 60,000 is above the 50,000 limit: accepted, with the reserve capped at the limit
        submit(ALICE, ALICE_SG_MOTOR, LocalDate.of(2026, 11, 1), "Hit a kerb, front wheel damaged", "60000.00")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.claimNumber").value(matchesPattern("CLM-\\d{6}")))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.estimatedLoss").value(60000.00))
                .andExpect(jsonPath("$.reserveAmount").value(50000.00))
                .andExpect(jsonPath("$.currency").value("SGD"));

        // Newest first in the claimant's list (3 seeded + 1 new)
        mvc.perform(get("/api/me/claims").header("X-User-Id", ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].incidentDescription").doesNotExist())
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].estimatedLoss").value(60000.00));

        // And visible to staff in the work queue (5 seeded + 1 new)
        mvc.perform(get("/api/claims").header("X-User-Id", PRIYA_STAFF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)));
    }

    @Test
    void futureIncidentDateIsRejected() throws Exception {
        submit(ALICE, ALICE_SG_MOTOR, LocalDate.now().plusDays(1), "Something will happen", "100")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("incidentDate"));
    }

    @Test
    void incidentBeforePolicyStartIsRejected() throws Exception {
        submit(ALICE, ALICE_SG_MOTOR, LocalDate.of(2025, 6, 1), "Scratched in a car park", "100")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("incidentDate"))
                .andExpect(jsonPath("$.errors[0].message").value(matchesPattern(".*policy period.*")));
    }

    @Test
    void claimAgainstSomeoneElsesPolicyIsRejected() throws Exception {
        submit(ALICE, BEN_MY_MOTOR, LocalDate.of(2026, 11, 1), "Not my policy at all", "100")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("policyId"))
                .andExpect(jsonPath("$.errors[0].message").value("Policy not found"));
    }

    @Test
    void shortDescriptionIsRejected() throws Exception {
        submit(ALICE, ALICE_SG_MOTOR, LocalDate.of(2026, 11, 1), "Too short", "100")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("incidentDescription"));
    }

    @Test
    void missingUserHeaderIsUnauthorized() throws Exception {
        mvc.perform(get("/api/me/claims")).andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotReportIncidents() throws Exception {
        submit(PRIYA_STAFF, ALICE_SG_MOTOR, LocalDate.of(2026, 11, 1), "Staff should not do this", "100")
                .andExpect(status().isForbidden());
    }

    @Test
    void claimantCannotSeeTheStaffQueue() throws Exception {
        mvc.perform(get("/api/claims").header("X-User-Id", ALICE)).andExpect(status().isForbidden());
    }

    // --- GET /api/claims/{id} ---

    static final String ALICE_AWAITING_INFO_CLAIM = "00000000-0000-0000-0000-000000001003"; // CLM-000003
    static final String BEN_CLAIM = "00000000-0000-0000-0000-000000001002";                 // CLM-000002

    @Test
    void claimantSeesOwnClaimWithDescriptionAndHistory() throws Exception {
        mvc.perform(get("/api/claims/" + ALICE_AWAITING_INFO_CLAIM).header("X-User-Id", ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.claimNumber").value("CLM-000003"))
                .andExpect(jsonPath("$.summary.status").value("AWAITING_INFO"))
                .andExpect(jsonPath("$.incidentDescription").value(matchesPattern("Burst pipe.*")))
                // Oldest first: submitted -> picked up -> info requested
                .andExpect(jsonPath("$.history", hasSize(3)))
                .andExpect(jsonPath("$.history[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$.history[0].toStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.history[0].changedByName").value("Alice Tan"))
                .andExpect(jsonPath("$.history[2].toStatus").value("AWAITING_INFO"))
                .andExpect(jsonPath("$.history[2].changedByName").value("Daniel Wong"));
    }

    @Test
    void claimantGets404ForSomeoneElsesClaim() throws Exception {
        mvc.perform(get("/api/claims/" + BEN_CLAIM).header("X-User-Id", ALICE))
                .andExpect(status().isNotFound());
    }

    @Test
    void staffCanSeeAnyClaim() throws Exception {
        mvc.perform(get("/api/claims/" + BEN_CLAIM).header("X-User-Id", PRIYA_STAFF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.claimantName").value("Ben Lim"));
    }

    @Test
    void unknownClaimIs404() throws Exception {
        mvc.perform(get("/api/claims/00000000-0000-0000-0000-999999999999").header("X-User-Id", PRIYA_STAFF))
                .andExpect(status().isNotFound());
    }

    @Test
    void locationHeaderFromSubmitPointsToTheNewClaim() throws Exception {
        String location = submit(ALICE, ALICE_SG_MOTOR, LocalDate.of(2026, 11, 1), "Cracked windscreen on the expressway", "800")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location).header("X-User-Id", ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.history", hasSize(1)));
    }

    @Test
    void claimantSeesOnlyTheirOwnPolicies() throws Exception {
        mvc.perform(get("/api/me/policies").header("X-User-Id", ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].policyNumber").value("SG-MOT-0001"))
                .andExpect(jsonPath("$[1].policyNumber").value("SG-PRP-0002"));
    }
}
