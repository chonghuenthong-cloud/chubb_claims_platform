import { Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ClaimService } from '../../claims/claim.service';
import { StatusPill } from '../../claims/status-pill';

/**
 * Claimant's minimal claim page: the officer's open question and an answer box.
 * The full claim tracking page (history, decisions) comes in the claimant tracking milestone.
 */
@Component({
  selector: 'app-answer-info',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, MatFormFieldModule, MatInputModule, MatButtonModule, StatusPill],
  templateUrl: './answer-info.html',
  styleUrl: './answer-info.scss',
})
export class AnswerInfo {
  private readonly claimService = inject(ClaimService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  /** Claim id from the route (/claimant/claims/:id). */
  readonly id = input.required<string>();

  protected readonly claim = toSignal(toObservable(this.id).pipe(switchMap((id) => this.claimService.getClaim(id))));

  /** The question still waiting for an answer, if any. */
  protected readonly openQuestion = computed(() =>
    [...(this.claim()?.informationRequests ?? [])].reverse().find((r) => !r.response) ?? null,
  );

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
      next: () => {
        this.snackBar.open('Answer sent', 'OK', { duration: 4000 });
        this.router.navigateByUrl('/claimant');
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
