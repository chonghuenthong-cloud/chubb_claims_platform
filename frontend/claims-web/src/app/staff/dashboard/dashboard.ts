import { Component, computed, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatTableModule } from '@angular/material/table';
import { Dashboard as DashboardData } from './dashboard.model';
import { DurationPipe } from './duration.pipe';

/**
 * Team dashboard: unassigned queue, liability exposure per currency, workload and performance per officer.
 * Loaded once when opened; the backend calculates everything live.
 */
@Component({
  selector: 'app-dashboard',
  imports: [MatTableModule, CurrencyPipe, DurationPipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  /** Undefined until loaded, so nothing (not zeros) shows while waiting. */
  protected readonly data = toSignal(inject(HttpClient).get<DashboardData>('/api/dashboard'));

  /** How long the oldest unassigned claim has waited, in hours (for the duration pipe). */
  protected readonly oldestWaitHours = computed(() => {
    const oldest = this.data()?.unassigned.oldestSubmittedAt;
    return oldest ? (Date.now() - new Date(oldest).getTime()) / 3_600_000 : null;
  });

  protected readonly workloadColumns = ['officer', 'underReview', 'awaitingInfo', 'approvedNotSettled', 'totalOpen'];
  protected readonly performanceColumns = [
    'officer', 'approved', 'rejected', 'settled', 'submissionToPickUp', 'pickUpToDecision', 'approvalToSettlement',
  ];

  /** "MOTOR" -> "Motor", "PROPERTY" -> "Property"; market codes stay as they are. */
  protected productLabel(key: string): string {
    return key.charAt(0) + key.slice(1).toLowerCase();
  }
}
