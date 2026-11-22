import { Component, computed, input } from '@angular/core';
import { ClaimStatus } from './claim.model';

/** Coloured status label, shared by the claimant and staff lists. */
@Component({
  selector: 'app-status-pill',
  template: `<span class="status status-{{ status() }}">{{ label() }}</span>`,
  styles: `
    .status {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 12px;
      font: var(--mat-sys-label-medium);
      white-space: nowrap;
    }
    .status-SUBMITTED     { background: #e3f2fd; color: #0d47a1; }
    .status-UNDER_REVIEW  { background: #fff3e0; color: #e65100; }
    .status-AWAITING_INFO { background: #f3e5f5; color: #6a1b9a; }
    .status-APPROVED      { background: #e8f5e9; color: #1b5e20; }
    .status-REJECTED      { background: #ffebee; color: #b71c1c; }
    .status-SETTLED       { background: #eceff1; color: #37474f; }
  `,
})
export class StatusPill {
  readonly status = input.required<ClaimStatus>();

  /** "UNDER_REVIEW" -> "Under review". */
  protected readonly label = computed(() => {
    const text = this.status().replace('_', ' ').toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
  });
}
