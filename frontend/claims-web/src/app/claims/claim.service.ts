import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ClaimDetail, ClaimSummary, Policy, QueueFilter, SubmitClaimRequest } from './claim.model';

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

  // --- Both roles ---

  /** One claim in full. Staff: any claim; claimant: own claims only. */
  getClaim(id: string): Observable<ClaimDetail> {
    return this.http.get<ClaimDetail>(`/api/claims/${id}`);
  }

  // --- Claimant ---

  /** Their own claims, newest first. */
  getMyClaims(): Observable<ClaimSummary[]> {
    return this.http.get<ClaimSummary[]>('/api/me/claims');
  }

  /** The policies they can claim against. */
  getMyPolicies(): Observable<Policy[]> {
    return this.http.get<Policy[]>('/api/me/policies');
  }

  /** Report an incident. Returns the new claim in SUBMITTED status. */
  submitClaim(request: SubmitClaimRequest): Observable<ClaimSummary> {
    return this.http.post<ClaimSummary>('/api/claims', request);
  }

  /** Answer the officer's open question. */
  answer(id: string, response: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`/api/claims/${id}/answer`, { response });
  }
}
