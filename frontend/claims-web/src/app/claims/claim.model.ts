/** Mirrors the backend ClaimStatus enum. */
export type ClaimStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'AWAITING_INFO'
  | 'APPROVED'
  | 'REJECTED'
  | 'SETTLED';

/**
 * One row in a claims list. Shape is provisional: once the REST API exists,
 * it will be replaced by the type generated from the backend's OpenAPI spec.
 */
export interface ClaimSummary {
  id: string;
  claimNumber: string;
  claimantName: string;
  productType: 'MOTOR' | 'PROPERTY';
  market: string;
  status: ClaimStatus;
  assignedOfficerName: string | null;
  reserveAmount: number;
  currency: string;
  submittedAt: string; // ISO 8601
}
