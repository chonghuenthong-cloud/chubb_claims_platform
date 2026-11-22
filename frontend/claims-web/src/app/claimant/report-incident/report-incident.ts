import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ClaimService } from '../../claims/claim.service';
import { FieldError, Policy } from '../../claims/claim.model';

/**
 * "Report an incident" form. Runs the same checks as the backend so most mistakes are caught before sending;
 * anything the backend still rejects is shown next to the matching field.
 */
@Component({
  selector: 'app-report-incident',
  imports: [
    ReactiveFormsModule, RouterLink, DatePipe,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatDatepickerModule, MatButtonModule,
  ],
  templateUrl: './report-incident.html',
  styleUrl: './report-incident.scss',
})
export class ReportIncident {
  private readonly claimService = inject(ClaimService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly policies = toSignal(this.claimService.getMyPolicies(), { initialValue: [] as Policy[] });
  protected readonly today = startOfToday();
  protected readonly submitting = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    policyId: ['', Validators.required],
    incidentDate: [null as Date | null, [Validators.required, notInFuture, withinSelectedPolicy(() => this.policies())]],
    incidentDescription: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(2000)]],
    estimatedLoss: [null as number | null, [Validators.required, Validators.min(0.01), atMostTwoDecimals]],
  });

  /** Policy chosen in the dropdown, for the date picker range and the currency shown on the amount. */
  private readonly policyId = toSignal(this.form.controls.policyId.valueChanges, { initialValue: '' });
  protected readonly selectedPolicy = computed(() => this.policies().find((p) => p.id === this.policyId()));

  constructor() {
    // The date's validity depends on the chosen policy, so re-check it when the policy changes
    this.form.controls.policyId.valueChanges.subscribe(() => this.form.controls.incidentDate.updateValueAndValidity());
  }

  /** Latest day the date picker allows: today, or the policy's last day if it ended earlier. */
  protected maxDate(policy: Policy | undefined): Date {
    if (!policy) return this.today;
    const lastCoveredDay = parseDate(policy.endDate);
    lastCoveredDay.setDate(lastCoveredDay.getDate() - 1);
    return lastCoveredDay < this.today ? lastCoveredDay : this.today;
  }

  protected parseDate = parseDate;

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.submitting.set(true);
    this.claimService
      .submitClaim({
        policyId: value.policyId,
        incidentDate: toIsoDate(value.incidentDate!),
        incidentDescription: value.incidentDescription.trim(),
        estimatedLoss: value.estimatedLoss!,
      })
      .subscribe({
        next: (claim) => {
          this.snackBar.open(`Claim ${claim.claimNumber} submitted`, 'OK', { duration: 5000 });
          this.router.navigateByUrl('/claimant');
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.showServerErrors(err);
        },
      });
  }

  /** Puts each backend field error on its form control; anything else becomes a general message. */
  private showServerErrors(err: HttpErrorResponse): void {
    const errors: FieldError[] = err.status === 400 ? (err.error?.errors ?? []) : [];
    const unmatched = errors.filter((e) => {
      const control = this.form.get(e.field);
      control?.setErrors({ server: e.message });
      control?.markAsTouched();
      return !control;
    });
    if (!errors.length || unmatched.length) {
      this.snackBar.open(err.error?.detail ?? 'Could not submit the claim. Please try again.', 'OK');
    }
  }
}

/** Incident date can't be after today (matches the backend's @PastOrPresent). */
function notInFuture(control: AbstractControl<Date | null>): ValidationErrors | null {
  return control.value && control.value > startOfToday() ? { future: true } : null;
}

/**
 * Incident date must fall in the chosen policy's period (start inclusive, end exclusive), like Policy.covers.
 * Takes the policy list as a function because it loads after the form is created.
 */
function withinSelectedPolicy(policies: () => Policy[]) {
  return (control: AbstractControl<Date | null>): ValidationErrors | null => {
    const policyId = control.parent?.get('policyId')?.value;
    const policy = policies().find((p) => p.id === policyId);
    const date = control.value;
    if (!date || !policy) return null;
    const covered = date >= parseDate(policy.startDate) && date < parseDate(policy.endDate);
    return covered ? null : { outsidePolicy: true };
  };
}

/** Matches the backend's @Digits(fraction = 2). */
function atMostTwoDecimals(control: AbstractControl<number | null>): ValidationErrors | null {
  const value = control.value;
  return value != null && Math.round(value * 100) !== value * 100 ? { decimals: true } : null;
}

function startOfToday(): Date {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

/** "2026-11-01" -> local midnight Date (avoids the UTC shift of new Date("2026-11-01")). */
function parseDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

/** Local Date -> "2026-11-01", the format the backend expects. */
function toIsoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
