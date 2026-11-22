package com.chubb.claims.dashboard;

import com.chubb.claims.dashboard.DashboardResponse.Breakdown;
import com.chubb.claims.dashboard.DashboardResponse.CurrencyExposure;
import com.chubb.claims.dashboard.DashboardResponse.OfficerPerformance;
import com.chubb.claims.dashboard.DashboardResponse.OfficerWorkload;
import com.chubb.claims.dashboard.DashboardResponse.UnassignedQueue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Team dashboard figures, calculated live with SQL aggregates on every request.
 *
 * Trade-off (decided): live queries are always exact and need no extra moving parts, which suits this scale.
 * At production volume these would be served from a read model kept up to date by Kafka consumers.
 */
@Service
public class DashboardService {

    /** Statuses that still carry liability. Rejected and settled claims are closed (and have a reserve of 0). */
    private static final String OPEN_STATUSES = "('SUBMITTED', 'UNDER_REVIEW', 'AWAITING_INFO', 'APPROVED')";

    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        return new DashboardResponse(unassigned(), workload(), exposure(), performance());
    }

    private UnassignedQueue unassigned() {
        return jdbc.queryForObject(
                "SELECT count(*) AS n, min(submitted_at) AS oldest FROM claim WHERE assigned_officer_id IS NULL",
                (rs, i) -> {
                    Timestamp oldest = rs.getTimestamp("oldest");
                    return new UnassignedQueue(rs.getLong("n"), oldest == null ? null : oldest.toInstant());
                });
    }

    /** Every staff member (LEFT JOIN keeps idle officers, showing zeros). */
    private List<OfficerWorkload> workload() {
        return jdbc.query("""
                SELECT u.full_name,
                       count(c.id) FILTER (WHERE c.status = 'UNDER_REVIEW')  AS under_review,
                       count(c.id) FILTER (WHERE c.status = 'AWAITING_INFO') AS awaiting_info,
                       count(c.id) FILTER (WHERE c.status = 'APPROVED')      AS approved
                FROM app_user u
                LEFT JOIN claim c ON c.assigned_officer_id = u.id
                WHERE u.role = 'STAFF'
                GROUP BY u.id, u.full_name
                ORDER BY u.full_name
                """, (rs, i) -> {
            long underReview = rs.getLong("under_review");
            long awaitingInfo = rs.getLong("awaiting_info");
            long approved = rs.getLong("approved");
            return new OfficerWorkload(rs.getString("full_name"), underReview, awaitingInfo, approved,
                    underReview + awaitingInfo + approved);
        });
    }

    /** Reserves on open claims, summed per currency, then split by market and by product. */
    private List<CurrencyExposure> exposure() {
        // One row per (currency, market, product); totals and breakdowns are added up below
        record Slice(String currency, String market, String product, BigDecimal amount) {
        }
        List<Slice> slices = jdbc.query("""
                SELECT p.currency, p.market, p.product_type, sum(c.reserve_amount) AS amount
                FROM claim c
                JOIN policy p ON p.id = c.policy_id
                WHERE c.status IN %s
                GROUP BY p.currency, p.market, p.product_type
                """.formatted(OPEN_STATUSES),
                (rs, i) -> new Slice(rs.getString("currency"), rs.getString("market"),
                        rs.getString("product_type"), rs.getBigDecimal("amount")));

        // TreeMaps keep currencies, markets and products in alphabetical order
        Map<String, Map<String, BigDecimal>> byMarket = new TreeMap<>();
        Map<String, Map<String, BigDecimal>> byProduct = new TreeMap<>();
        for (Slice s : slices) {
            byMarket.computeIfAbsent(s.currency(), k -> new TreeMap<>()).merge(s.market(), s.amount(), BigDecimal::add);
            byProduct.computeIfAbsent(s.currency(), k -> new TreeMap<>()).merge(s.product(), s.amount(), BigDecimal::add);
        }

        List<CurrencyExposure> result = new ArrayList<>();
        byMarket.forEach((currency, markets) -> {
            BigDecimal total = markets.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new CurrencyExposure(currency, total, toBreakdown(markets), toBreakdown(byProduct.get(currency))));
        });
        return result;
    }

    private static List<Breakdown> toBreakdown(Map<String, BigDecimal> amounts) {
        return amounts.entrySet().stream().map(e -> new Breakdown(e.getKey(), e.getValue())).toList();
    }

    /** Counts per officer from the status history, plus three average durations. */
    private List<OfficerPerformance> performance() {
        // Officer id -> average hours, one map per duration
        Map<UUID, Double> submissionToPickUp = averageHours("""
                SELECT h.changed_by AS officer, avg(extract(epoch FROM h.changed_at - c.submitted_at)) / 3600 AS hours
                FROM claim_status_history h
                JOIN claim c ON c.id = h.claim_id
                WHERE h.from_status = 'SUBMITTED' AND h.to_status = 'UNDER_REVIEW'
                GROUP BY h.changed_by
                """);
        // Credited to the officer who decided. Includes any time spent waiting for the claimant's answer.
        Map<UUID, Double> pickUpToDecision = averageHours("""
                SELECT d.changed_by AS officer, avg(extract(epoch FROM d.changed_at - p.changed_at)) / 3600 AS hours
                FROM claim_status_history d
                JOIN claim_status_history p ON p.claim_id = d.claim_id
                     AND p.from_status = 'SUBMITTED' AND p.to_status = 'UNDER_REVIEW'
                WHERE d.to_status IN ('APPROVED', 'REJECTED')
                GROUP BY d.changed_by
                """);
        // Credited to the officer who settled
        Map<UUID, Double> approvalToSettlement = averageHours("""
                SELECT s.changed_by AS officer, avg(extract(epoch FROM s.changed_at - a.changed_at)) / 3600 AS hours
                FROM claim_status_history s
                JOIN claim_status_history a ON a.claim_id = s.claim_id AND a.to_status = 'APPROVED'
                WHERE s.to_status = 'SETTLED'
                GROUP BY s.changed_by
                """);

        return jdbc.query("""
                SELECT u.id, u.full_name,
                       count(h.id) FILTER (WHERE h.to_status = 'APPROVED') AS approved,
                       count(h.id) FILTER (WHERE h.to_status = 'REJECTED') AS rejected,
                       count(h.id) FILTER (WHERE h.to_status = 'SETTLED')  AS settled
                FROM app_user u
                LEFT JOIN claim_status_history h ON h.changed_by = u.id
                WHERE u.role = 'STAFF'
                GROUP BY u.id, u.full_name
                ORDER BY u.full_name
                """, (rs, i) -> {
            UUID officer = rs.getObject("id", UUID.class);
            return new OfficerPerformance(rs.getString("full_name"),
                    rs.getLong("approved"), rs.getLong("rejected"), rs.getLong("settled"),
                    submissionToPickUp.get(officer), pickUpToDecision.get(officer), approvalToSettlement.get(officer));
        });
    }

    /** Runs a query returning (officer, hours) rows and collects them into a map. */
    private Map<UUID, Double> averageHours(String sql) {
        Map<UUID, Double> result = new HashMap<>();
        jdbc.query(sql, rs -> {
            result.put(rs.getObject("officer", UUID.class), rs.getDouble("hours"));
        });
        return result;
    }
}
