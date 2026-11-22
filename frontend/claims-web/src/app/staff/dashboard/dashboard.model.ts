/** Mirrors the backend DashboardResponse (GET /api/dashboard). All figures are all-time. */
export interface Dashboard {
  unassigned: { count: number; oldestSubmittedAt: string | null };
  workload: OfficerWorkload[];
  exposure: CurrencyExposure[];
  performance: OfficerPerformance[];
}

export interface OfficerWorkload {
  officerName: string;
  underReview: number;
  awaitingInfo: number;
  approvedNotSettled: number;
  totalOpen: number;
}

/** Sum of reserves on open claims in one currency (no FX conversion). */
export interface CurrencyExposure {
  currency: string;
  total: number;
  byMarket: Breakdown[];
  byProduct: Breakdown[];
}

export interface Breakdown {
  key: string;
  amount: number;
}

/** Average durations are in hours; null when there is nothing to average yet. */
export interface OfficerPerformance {
  officerName: string;
  approved: number;
  rejected: number;
  settled: number;
  avgSubmissionToPickUpHours: number | null;
  avgPickUpToDecisionHours: number | null;
  avgApprovalToSettlementHours: number | null;
}
