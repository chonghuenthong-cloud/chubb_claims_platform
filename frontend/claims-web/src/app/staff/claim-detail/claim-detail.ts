import { Component, computed, inject, input, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap, tap } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ClaimService } from '../../claims/claim.service';
import { ClaimDetail as ClaimDetailModel } from '../../claims/claim.model';
import { StatusPill } from '../../claims/status-pill';
import { CurrentUserService } from '../../core/current-user.service';
import { ActionDialog, ActionDialogData } from './action-dialog';

/**
 * Staff view of one claim: details, money, information requests, history, and the actions that fit
 * the claim's status and whether the viewer is its assigned officer.
 */
@Component({
  selector: 'app-claim-detail',
  imports: [RouterLink, CurrencyPipe, DatePipe, MatButtonModule, StatusPill],
  templateUrl: './claim-detail.html',
  styleUrl: './claim-detail.scss',
})
export class ClaimDetail {
  private readonly claimService = inject(ClaimService);
  private readonly currentUser = inject(CurrentUserService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  /** Claim id from the route (/staff/claims/:id), bound by the router. */
  readonly id = input.required<string>();

  /** The claim; replaced by the server's response after each action. */
  protected readonly claim = signal<ClaimDetailModel | null>(null);

  constructor() {
    toObservable(this.id)
      .pipe(switchMap((id) => this.claimService.getClaim(id)), tap((c) => this.claim.set(c)))
      .subscribe();
  }

  /** True when the viewer is the claim's assigned officer: only they can act on it. */
  private readonly isMine = computed(() => this.claim()?.summary.assignedOfficerId === this.currentUser.user()?.id);

  /** Which buttons to show, from status and ownership (the backend enforces the same rules). */
  protected readonly actions = computed(() => {
    const c = this.claim();
    if (!c) return { pickUp: false, reserve: false, requestInfo: false, decide: false, settle: false };
    const status = c.summary.status;
    const mine = this.isMine();
    return {
      pickUp: status === 'SUBMITTED' && !c.summary.assignedOfficerId,
      reserve: mine && (status === 'UNDER_REVIEW' || status === 'AWAITING_INFO'),
      requestInfo: mine && status === 'UNDER_REVIEW',
      decide: mine && status === 'UNDER_REVIEW',
      settle: mine && status === 'APPROVED',
    };
  });

  // --- Actions ---

  protected pickUp(): void {
    this.claimService.pickUp(this.id()).subscribe({
      next: (c) => this.done(c, 'Claim picked up'),
      error: (err) => this.snackBar.open(err.error?.detail ?? 'Could not pick up the claim', 'OK'),
    });
  }

  protected reviseReserve(): void {
    const c = this.claim()!;
    this.open({
      title: 'Revise reserve',
      confirmLabel: 'Save reserve',
      input: { kind: 'amount', label: 'Reserve', currency: c.summary.currency, initial: c.summary.reserveAmount, min: 0, max: c.coverageLimit },
      submit: (v) => this.claimService.reviseReserve(this.id(), Number(v)),
    }, 'Reserve updated');
  }

  protected requestInfo(): void {
    this.open({
      title: 'Request information',
      confirmLabel: 'Send question',
      input: { kind: 'text', label: 'Question for the claimant' },
      submit: (v) => this.claimService.requestInfo(this.id(), String(v)),
    }, 'Question sent');
  }

  protected approve(): void {
    const c = this.claim()!;
    this.open({
      title: 'Approve claim',
      confirmLabel: 'Approve',
      irreversible: true,
      input: { kind: 'amount', label: 'Approved amount', currency: c.summary.currency, initial: c.summary.reserveAmount, min: 0.01, max: c.coverageLimit },
      submit: (v) => this.claimService.approve(this.id(), Number(v)),
    }, 'Claim approved');
  }

  protected reject(): void {
    this.open({
      title: 'Reject claim',
      confirmLabel: 'Reject',
      irreversible: true,
      input: { kind: 'text', label: 'Reason (shown to the claimant)' },
      submit: (v) => this.claimService.reject(this.id(), String(v)),
    }, 'Claim rejected');
  }

  protected settle(): void {
    const c = this.claim()!;
    this.open({
      title: `Mark as settled: ${c.summary.currency} ${c.approvedAmount?.toLocaleString()} paid?`,
      confirmLabel: 'Mark as settled',
      irreversible: true,
      submit: () => this.claimService.settle(this.id()),
    }, 'Claim settled');
  }

  private open(data: ActionDialogData, successMessage: string): void {
    this.dialog.open(ActionDialog, { data }).afterClosed().subscribe((updated?: ClaimDetailModel) => {
      if (updated) this.done(updated, successMessage);
    });
  }

  private done(updated: ClaimDetailModel, message: string): void {
    this.claim.set(updated);
    this.snackBar.open(message, 'OK', { duration: 4000 });
  }
}
