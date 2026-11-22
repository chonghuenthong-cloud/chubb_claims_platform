import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { ClaimDetail } from '../../claims/claim.model';

/** Configuration for one action (approve, reject, revise reserve, ...). */
export interface ActionDialogData {
  title: string;
  confirmLabel: string;
  /** Shows "This can't be undone." for actions with no way back (approve, reject, settle). */
  irreversible?: boolean;
  /** Omit for a plain confirmation (settle). */
  input?: {
    kind: 'amount' | 'text';
    label: string;
    currency?: string;
    initial?: number;
    /** Upper bound for amounts (the coverage limit). */
    max?: number;
    /** Lower bound for amounts: 0 for reserve, 0.01 for approved amount. */
    min?: number;
  };
  /** Calls the API with the entered value; the dialog stays open and shows the error if it fails. */
  submit: (value: string | number | null) => Observable<ClaimDetail>;
}

/**
 * One dialog for every claim action. Runs the same checks as the backend, sends the request itself,
 * and only closes on success, so backend errors (403/409/400) are shown where the officer is looking.
 */
@Component({
  selector: 'app-action-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      @if (data.input; as input) {
        <mat-form-field appearance="outline" class="field">
          <mat-label>{{ input.label }}</mat-label>
          <!-- One element per block, so Material can place each in its slot (prefix, input, hint) -->
          @if (input.kind === 'amount') { <span matTextPrefix>{{ input.currency }}&nbsp;</span> }
          @if (input.kind === 'amount') {
            <input matInput type="number" step="0.01" [formControl]="control" cdkFocusInitial>
          } @else {
            <textarea matInput rows="4" [formControl]="control" cdkFocusInitial></textarea>
          }
          @if (input.kind === 'text') { <mat-hint align="end">{{ textLength() }} / 2000</mat-hint> }
          @let errors = control.errors;
          @if (errors?.['required']) { <mat-error>Required</mat-error> }
          @else if (errors?.['min']) { <mat-error>Must be at least {{ input.min }}</mat-error> }
          @else if (errors?.['max']) { <mat-error>Cannot exceed the coverage limit of {{ input.currency }} {{ input.max }}</mat-error> }
          @else if (errors?.['decimals']) { <mat-error>At most 2 decimal places</mat-error> }
          @else if (errors?.['minlength'] || errors?.['maxlength']) { <mat-error>Must be 10 to 2000 characters</mat-error> }
          @else if (errors?.['server']) { <mat-error>{{ errors?.['server'] }}</mat-error> }
        </mat-form-field>
      }
      @if (data.irreversible) { <p class="warning">This can't be undone.</p> }
      @if (error()) { <p class="error">{{ error() }}</p> }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button (click)="confirm()" [disabled]="busy()">{{ data.confirmLabel }}</button>
    </mat-dialog-actions>
  `,
  styles: `
    .field { width: 420px; max-width: 100%; }
    .warning { color: var(--mat-sys-on-surface-variant); margin: 0; }
    .error { color: var(--mat-sys-error); margin: 8px 0 0; }
  `,
})
export class ActionDialog {
  protected readonly data = inject<ActionDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ActionDialog, ClaimDetail>);

  protected readonly control = new FormControl<string | number | null>(
    this.data.input?.initial ?? null,
    this.validators(),
  );
  protected readonly busy = signal(false);
  /** Errors that don't belong to the input (e.g. "Only the assigned officer can do this"). */
  protected readonly error = signal<string | null>(null);

  protected textLength(): number {
    return String(this.control.value ?? '').length;
  }

  protected confirm(): void {
    if (this.control.invalid && this.data.input) {
      this.control.markAsTouched();
      return;
    }
    const value = this.data.input?.kind === 'text' ? String(this.control.value ?? '').trim() : this.control.value;
    this.busy.set(true);
    this.error.set(null);
    this.data.submit(value).subscribe({
      next: (claim) => this.dialogRef.close(claim),
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        const fieldError = err.status === 400 ? err.error?.errors?.[0]?.message : null;
        if (fieldError && this.data.input) {
          this.control.setErrors({ server: fieldError });
          this.control.markAsTouched();
        } else {
          this.error.set(err.error?.detail ?? 'Something went wrong. Please try again.');
        }
      },
    });
  }

  /** Mirrors the backend rules for the configured input. */
  private validators(): ValidatorFn[] {
    const input = this.data.input;
    if (!input) return [];
    if (input.kind === 'text') {
      return [Validators.required, Validators.minLength(10), Validators.maxLength(2000)];
    }
    const result: ValidatorFn[] = [Validators.required, atMostTwoDecimals];
    if (input.min != null) result.push(Validators.min(input.min));
    if (input.max != null) result.push(Validators.max(input.max));
    return result;
  }
}

/** Matches the backend's @Digits(fraction = 2). */
const atMostTwoDecimals: ValidatorFn = (control) => {
  const value = control.value;
  return value != null && value !== '' && Math.round(value * 100) !== value * 100 ? { decimals: true } : null;
};
