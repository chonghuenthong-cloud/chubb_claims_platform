import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ClaimSummary, Policy, SubmitClaimRequest } from './claim.model';

/** Claim API calls. The user header is added by userHeaderInterceptor. */
@Injectable({ providedIn: 'root' })
export class ClaimService {
  private readonly http = inject(HttpClient);

  /** Staff: every claim, newest first. */
  getAllClaims(): Observable<ClaimSummary[]> {
    return this.http.get<ClaimSummary[]>('/api/claims');
  }

  /** Claimant: their own claims, newest first. */
  getMyClaims(): Observable<ClaimSummary[]> {
    return this.http.get<ClaimSummary[]>('/api/me/claims');
  }

  /** Claimant: the policies they can claim against. */
  getMyPolicies(): Observable<Policy[]> {
    return this.http.get<Policy[]>('/api/me/policies');
  }

  /** Claimant: report an incident. Returns the new claim in SUBMITTED status. */
  submitClaim(request: SubmitClaimRequest): Observable<ClaimSummary> {
    return this.http.post<ClaimSummary>('/api/claims', request);
  }
}
