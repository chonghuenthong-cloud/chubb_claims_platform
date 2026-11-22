import { Component, inject } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatTableModule } from '@angular/material/table';
import { ClaimService } from '../../claims/claim.service';
import { StatusPill } from '../../claims/status-pill';

/** Staff view: every claim, so officers can see what is waiting and who holds what. */
@Component({
  selector: 'app-work-queue',
  imports: [MatTableModule, CurrencyPipe, DatePipe, StatusPill],
  templateUrl: './work-queue.html',
  styleUrl: './work-queue.scss',
})
export class WorkQueue {
  private readonly claimService = inject(ClaimService);

  /** Claims from GET /api/claims; empty until loaded. */
  protected readonly claims = toSignal(this.claimService.getAllClaims(), { initialValue: [] });

  protected readonly columns = [
    'claimNumber', 'claimant', 'product', 'market', 'status', 'officer', 'reserve', 'submitted',
  ];
}
