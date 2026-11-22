import { Component, inject } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { filter, switchMap } from 'rxjs';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { ClaimService } from '../../claims/claim.service';
import { StatusPill } from '../../claims/status-pill';
import { CurrentUserService } from '../../core/current-user.service';

/** Claimant view: their own claims, newest first, and the way in to report a new incident. */
@Component({
  selector: 'app-my-claims',
  imports: [MatTableModule, MatButtonModule, RouterLink, CurrencyPipe, DatePipe, StatusPill],
  templateUrl: './my-claims.html',
  styleUrl: './my-claims.scss',
})
export class MyClaims {
  private readonly claimService = inject(ClaimService);

  /** Reloads whenever the "Viewing as" user changes (e.g. Alice -> Ben stays on this page). */
  protected readonly claims = toSignal(
    toObservable(inject(CurrentUserService).user).pipe(
      filter((user) => user?.role === 'CLAIMANT'),
      switchMap(() => this.claimService.getMyClaims()),
    ),
  );

  protected readonly columns = ['claimNumber', 'product', 'status', 'estimate', 'submitted'];
}
