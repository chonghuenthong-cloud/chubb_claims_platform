import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import { MatTableModule } from '@angular/material/table';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { ClaimService } from '../../claims/claim.service';
import { ClaimSummary, QueueFilter } from '../../claims/claim.model';
import { StatusPill } from '../../claims/status-pill';

/** Staff view: claims to work on, filtered by assignment. Clicking a row opens the claim. */
@Component({
  selector: 'app-work-queue',
  imports: [MatTableModule, MatButtonToggleModule, CurrencyPipe, DatePipe, StatusPill],
  templateUrl: './work-queue.html',
  styleUrl: './work-queue.scss',
})
export class WorkQueue {
  private readonly claimService = inject(ClaimService);
  private readonly router = inject(Router);

  protected readonly filter = signal<QueueFilter>('ALL');

  /**
   * Reloads from GET /api/claims whenever the filter changes.
   * Undefined until the first response, so "No claims." only shows for a truly empty result (same as My claims).
   */
  protected readonly claims = toSignal(
    toObservable(this.filter).pipe(switchMap((f) => this.claimService.getQueue(f))),
  );

  protected readonly columns = [
    'claimNumber', 'claimant', 'product', 'market', 'status', 'officer', 'reserve', 'submitted',
  ];

  protected open(claim: ClaimSummary): void {
    this.router.navigate(['/staff/claims', claim.id]);
  }
}
