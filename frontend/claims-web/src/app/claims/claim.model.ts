/** Mirrors the backend ClaimStatus enum. */
export type ClaimStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'AWAITING_INFO'
  | 'APPROVED'
  | 'REJECTED'
  | 'SETTLED';

export type ProductType = 'MOTOR' | 'PROPERTY';

/**
 * Hand-written to match the backend DTOs (ClaimSummaryResponse, PolicyResponse, SubmitClaimRequest).
 * The backend's OpenAPI spec at /v3/api-docs is the source of truth; these could be generated from it later.
 */

/** One claim in a list (claimant's "My claims" and the staff work queue). */
export interface ClaimSummary {
  id: string;
  claimNumber: string;
  policyNumber: string;
  claimantName: string;
  productType: ProductType;
  market: string;
  status: ClaimStatus;
  assignedOfficerName: string | null;
  incidentDate: string; // YYYY-MM-DD
  estimatedLoss: number;
  reserveAmount: number;
  currency: string;
  submittedAt: string; // ISO 8601 instant
}

/** A policy the claimant can claim against. */
export interface Policy {
  id: string;
  policyNumber: string;
  market: string;
  productType: ProductType;
  coverageLimit: number;
  currency: string;
  startDate: string; // YYYY-MM-DD, inclusive
  endDate: string; // YYYY-MM-DD, exclusive
}

/** Body of POST /api/claims. */
export interface SubmitClaimRequest {
  policyId: string;
  incidentDate: string; // YYYY-MM-DD
  incidentDescription: string;
  estimatedLoss: number;
}

/** A field error from the backend's Problem Details response. */
export interface FieldError {
  field: string;
  message: string;
}
