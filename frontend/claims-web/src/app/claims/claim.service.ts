import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { ClaimSummary } from './claim.model';

/**
 * Source of claim data for the screens.
 *
 * TEMPORARY: returns mock data matching the backend seed (V100__seed_demo_data.sql).
 * When the REST API lands, only this class changes: the methods keep returning Observables,
 * so components don't need to change.
 */
@Injectable({ providedIn: 'root' })
export class ClaimService {

  getClaims(): Observable<ClaimSummary[]> {
    return of(MOCK_CLAIMS);
  }
}

const MOCK_CLAIMS: ClaimSummary[] = [
  {
    id: '00000000-0000-0000-0000-000000001001', claimNumber: 'CLM-000001', claimantName: 'Alice Tan',
    productType: 'MOTOR', market: 'SG', status: 'SUBMITTED', assignedOfficerName: null,
    reserveAmount: 4200, currency: 'SGD', submittedAt: '2026-11-19T01:15:00Z',
  },
  {
    id: '00000000-0000-0000-0000-000000001002', claimNumber: 'CLM-000002', claimantName: 'Ben Lim',
    productType: 'MOTOR', market: 'MY', status: 'UNDER_REVIEW', assignedOfficerName: 'Priya Nair',
    reserveAmount: 12000, currency: 'MYR', submittedAt: '2026-11-11T06:02:00Z',
  },
  {
    id: '00000000-0000-0000-0000-000000001003', claimNumber: 'CLM-000003', claimantName: 'Alice Tan',
    productType: 'PROPERTY', market: 'SG', status: 'AWAITING_INFO', assignedOfficerName: 'Daniel Wong',
    reserveAmount: 25000, currency: 'SGD', submittedAt: '2026-11-03T00:40:00Z',
  },
  {
    id: '00000000-0000-0000-0000-000000001004', claimNumber: 'CLM-000004', claimantName: 'Ben Lim',
    productType: 'MOTOR', market: 'MY', status: 'APPROVED', assignedOfficerName: 'Priya Nair',
    reserveAmount: 8000, currency: 'MYR', submittedAt: '2026-10-21T02:30:00Z',
  },
  {
    id: '00000000-0000-0000-0000-000000001005', claimNumber: 'CLM-000005', claimantName: 'Alice Tan',
    productType: 'MOTOR', market: 'SG', status: 'REJECTED', assignedOfficerName: 'Daniel Wong',
    reserveAmount: 0, currency: 'SGD', submittedAt: '2026-10-05T08:20:00Z',
  },
];
