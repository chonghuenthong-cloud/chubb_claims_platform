package com.chubb.claims.dashboard;

import com.chubb.claims.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dashboard figures checked against hand-calculated values from the seed data
 * (db/seed/V100__seed_demo_data.sql). Times in the seed are +08:00.
 */
class DashboardIntegrationTest extends IntegrationTestBase {

    private ResultActions dashboard() throws Exception {
        return mvc.perform(get("/api/dashboard").header("X-User-Id", PRIYA_STAFF));
    }

    @Test
    void unassignedQueueShowsCountAndOldestClaim() throws Exception {
        // Only CLM-000001 is unassigned, submitted 2026-11-19 09:15 +08:00
        dashboard()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unassigned.count").value(1))
                .andExpect(jsonPath("$.unassigned.oldestSubmittedAt").value("2026-11-19T01:15:00Z"));
    }

    @Test
    void workloadListsEveryOfficerByName() throws Exception {
        dashboard()
                .andExpect(jsonPath("$.workload", hasSize(2)))
                // Daniel: CLM-000003 awaiting info (CLM-000005 is rejected, so closed)
                .andExpect(jsonPath("$.workload[0].officerName").value("Daniel Wong"))
                .andExpect(jsonPath("$.workload[0].underReview").value(0))
                .andExpect(jsonPath("$.workload[0].awaitingInfo").value(1))
                .andExpect(jsonPath("$.workload[0].approvedNotSettled").value(0))
                .andExpect(jsonPath("$.workload[0].totalOpen").value(1))
                // Priya: CLM-000002 under review, CLM-000004 approved but not settled
                .andExpect(jsonPath("$.workload[1].officerName").value("Priya Nair"))
                .andExpect(jsonPath("$.workload[1].underReview").value(1))
                .andExpect(jsonPath("$.workload[1].approvedNotSettled").value(1))
                .andExpect(jsonPath("$.workload[1].totalOpen").value(2));
    }

    @Test
    void exposureSumsOpenReservesPerCurrencyWithBreakdowns() throws Exception {
        dashboard()
                .andExpect(jsonPath("$.exposure", hasSize(2)))
                // MYR: CLM-000002 (12,000) + CLM-000004 approved (8,000), both MY motor
                .andExpect(jsonPath("$.exposure[0].currency").value("MYR"))
                .andExpect(jsonPath("$.exposure[0].total").value(20000.00))
                .andExpect(jsonPath("$.exposure[0].byMarket[0].key").value("MY"))
                .andExpect(jsonPath("$.exposure[0].byProduct[0].key").value("MOTOR"))
                .andExpect(jsonPath("$.exposure[0].byProduct[0].amount").value(20000.00))
                // SGD: CLM-000001 motor (4,200) + CLM-000003 property (25,000); CLM-000005 rejected adds 0
                .andExpect(jsonPath("$.exposure[1].currency").value("SGD"))
                .andExpect(jsonPath("$.exposure[1].total").value(29200.00))
                .andExpect(jsonPath("$.exposure[1].byMarket[0].amount").value(29200.00))
                .andExpect(jsonPath("$.exposure[1].byProduct[0].key").value("MOTOR"))
                .andExpect(jsonPath("$.exposure[1].byProduct[0].amount").value(4200.00))
                .andExpect(jsonPath("$.exposure[1].byProduct[1].key").value("PROPERTY"))
                .andExpect(jsonPath("$.exposure[1].byProduct[1].amount").value(25000.00));
    }

    @Test
    void performanceCountsDecisionsAndAveragesDurations() throws Exception {
        dashboard()
                // Daniel: rejected CLM-000005
                .andExpect(jsonPath("$.performance[0].officerName").value("Daniel Wong"))
                .andExpect(jsonPath("$.performance[0].approved").value(0))
                .andExpect(jsonPath("$.performance[0].rejected").value(1))
                .andExpect(jsonPath("$.performance[0].settled").value(0))
                // Picked up CLM-000003 after 25h20m and CLM-000005 after 17h10m: average 21.25 h
                .andExpect(jsonPath("$.performance[0].avgSubmissionToPickUpHours").value(closeTo(21.25, 0.01)))
                // CLM-000005: picked up 09:30, rejected 10:05 -> 35 min
                .andExpect(jsonPath("$.performance[0].avgPickUpToDecisionHours").value(closeTo(0.583, 0.01)))
                .andExpect(jsonPath("$.performance[0].avgApprovalToSettlementHours").doesNotExist())
                // Priya: approved CLM-000004
                .andExpect(jsonPath("$.performance[1].officerName").value("Priya Nair"))
                .andExpect(jsonPath("$.performance[1].approved").value(1))
                .andExpect(jsonPath("$.performance[1].rejected").value(0))
                // Picked up CLM-000002 after 18h58m and CLM-000004 after 22h40m: average 20.82 h
                .andExpect(jsonPath("$.performance[1].avgSubmissionToPickUpHours").value(closeTo(20.817, 0.01)))
                // CLM-000004: picked up 22 Oct 09:10, approved 25 Oct 15:45 -> 78h35m
                .andExpect(jsonPath("$.performance[1].avgPickUpToDecisionHours").value(closeTo(78.583, 0.01)))
                .andExpect(jsonPath("$.performance[1].avgApprovalToSettlementHours").doesNotExist());
    }

    @Test
    void settlingUpdatesExposureAndPerformance() throws Exception {
        // Priya settles CLM-000004 (approved, MYR 8,000)
        mvc.perform(post("/api/claims/00000000-0000-0000-0000-000000001004/settle").header("X-User-Id", PRIYA_STAFF))
                .andExpect(status().isOk());

        dashboard()
                .andExpect(jsonPath("$.exposure[0].currency").value("MYR"))
                .andExpect(jsonPath("$.exposure[0].total").value(12000.00))   // only CLM-000002 left
                .andExpect(jsonPath("$.workload[1].approvedNotSettled").value(0))
                .andExpect(jsonPath("$.performance[1].settled").value(1))
                .andExpect(jsonPath("$.performance[1].avgApprovalToSettlementHours").exists());
    }

    @Test
    void claimantsCannotSeeTheDashboard() throws Exception {
        mvc.perform(get("/api/dashboard").header("X-User-Id", ALICE)).andExpect(status().isForbidden());
    }
}
