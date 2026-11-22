import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ClaimantClaimDetail, ClaimantClaimSummary, ClaimDetail, ClaimSummary, Policy, QueueFilter, SubmitClaimRequest,
} from './claim.model';

/** Claim API calls. The user header is added by userHeaderInterceptor. Every action returns the updated claim. */
@Injectable({ providedIn: 'root' })
export class ClaimService {
  private readonly http = inject(HttpClient);

  // --- Staff ---

  /** Work queue, newest first. */
  getQueue(filter: QueueFilter): Observable<ClaimSummary[]> {
    return this.http.get<ClaimSummary[]>('/api/claims', { params: { filter } });
  }

  pickUp(id: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`/api/claims/${id}/pick-up`, null);
  }

  reviseReserve(id: string, amount: number): Observable<ClaimDetail> {
    return this.http.put<ClaimDetail>(`/api/claims/${id}/reserve`, { amount });
  }

  requestInfo(id: string, question: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`/api/claims/${id}/request-info`, { question });
  }

  approve(id: string, amount: number): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`/api/claims/${id}/approve`, { amount });
  }

  reject(id: string, reason: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`/api/claims/${id}/reject`, { reason });
  }

  settle(id: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`/api/claims/${id}/settle`, null);
  }

  /** Any claim in full, including the reserve. */
  getClaim(id: string): Observable<ClaimDetail> {
    return this.http.get<ClaimDetail>(`/api/claims/${id}`);
  }

  // --- Claimant ---

  // Claimant calls return claimant-facing types without internal figures (no reserve).

  /** Their own claims, newest first. */
  getMyClaims(): Observable<ClaimantClaimSummary[]> {
    return this.http.get<ClaimantClaimSummary[]>('/api/me/claims');
  }

  /** One of their own claims: status, decision, timeline and questions. */
  getMyClaim(id: string): Observable<ClaimantClaimDetail> {
    return this.http.get<ClaimantClaimDetail>(`/api/me/claims/${id}`);
  }

  /** The policies they can claim against. */
  getMyPolicies(): Observable<Policy[]> {
    return this.http.get<Policy[]>('/api/me/policies');
  }

  /** Report an incident. Returns the new claim in SUBMITTED status. */
  submitClaim(request: SubmitClaimRequest): Observable<ClaimantClaimSummary> {
    return this.http.post<ClaimantClaimSummary>('/api/claims', request);
  }

  /** Answer the officer's open question. */
  answer(id: string, response: string): Observable<ClaimantClaimDetail> {
    return this.http.post<ClaimantClaimDetail>(`/api/claims/${id}/answer`, { response });
  }
}
