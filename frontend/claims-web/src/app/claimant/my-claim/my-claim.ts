import { Component, computed, inject, input, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { toObservable } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ClaimService } from '../../claims/claim.service';
import { ClaimantClaimDetail } from '../../claims/claim.model';
import { StatusPill } from '../../claims/status-pill';

/**
 * Claimant's claim page: decision, details, questions (with an answer box when one is open) and timeline.
 * Uses the claimant-facing API, which never includes internal figures such as the reserve.
 * Up to date whenever opened; live push updates come with the Kafka milestone.
 */
@Component({
  selector: 'app-my-claim',
  imports: [ReactiveFormsModule, RouterLink, CurrencyPipe, DatePipe, MatFormFieldModule, MatInputModule, MatButtonModule, StatusPill],
  templateUrl: './my-claim.html',
  styleUrl: './my-claim.scss',
})
export class MyClaim {
  private readonly claimService = inject(ClaimService);
  private readonly snackBar = inject(MatSnackBar);

  /** Claim id from the route (/claimant/claims/:id). */
  readonly id = input.required<string>();

  /** The claim; replaced by the server's response after answering. */
  protected readonly claim = signal<ClaimantClaimDetail | null>(null);

  constructor() {
    toObservable(this.id)
      .pipe(switchMap((id) => this.claimService.getMyClaim(id)))
      .subscribe((c) => this.claim.set(c));
  }

  /** The question still waiting for an answer, if any (only possible while Awaiting info). */
  protected readonly openQuestion = computed(() => {
    const c = this.claim();
    if (c?.summary.status !== 'AWAITING_INFO') return null;
    return [...c.informationRequests].reverse().find((r) => !r.response) ?? null;
  });

  protected readonly answer = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.minLength(10), Validators.maxLength(2000)],
  });
  protected readonly sending = signal(false);

  protected send(): void {
    if (this.answer.invalid) {
      this.answer.markAsTouched();
      return;
    }
    this.sending.set(true);
    this.claimService.answer(this.id(), this.answer.value.trim()).subscribe({
      next: (updated) => {
        // Stay on the page: the answer now shows in the Q&A and the status is back to Under review
        this.sending.set(false);
        this.answer.reset();
        this.claim.set(updated);
        this.snackBar.open('Answer sent', 'OK', { duration: 4000 });
      },
      error: (err: HttpErrorResponse) => {
        this.sending.set(false);
        const fieldError = err.status === 400 ? err.error?.errors?.[0]?.message : null;
        if (fieldError) {
          this.answer.setErrors({ server: fieldError });
        } else {
          this.snackBar.open(err.error?.detail ?? 'Could not send the answer. Please try again.', 'OK');
        }
      },
    });
  }
}
