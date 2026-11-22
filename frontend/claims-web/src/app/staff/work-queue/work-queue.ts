import { Component, inject } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatTableModule } from '@angular/material/table';
import { ClaimService } from '../../claims/claim.service';
import { ClaimStatus } from '../../claims/claim.model';

/** Staff view: every claim, so officers can see what is waiting and who holds what. */
@Component({
  selector: 'app-work-queue',
  imports: [MatTableModule, CurrencyPipe, DatePipe],
  templateUrl: './work-queue.html',
  styleUrl: './work-queue.scss',
})
export class WorkQueue {
  private readonly claimService = inject(ClaimService);

  /** Claims as a signal; starts empty until the Observable emits. */
  protected readonly claims = toSignal(this.claimService.getClaims(), { initialValue: [] });

  protected readonly columns = [
    'claimNumber', 'claimant', 'product', 'market', 'status', 'officer', 'reserve', 'submitted',
  ];

  /** "UNDER_REVIEW" -> "Under review" for display. */
  protected statusLabel(status: ClaimStatus): string {
    const text = status.replace('_', ' ').toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
  }
}
