import { Routes } from '@angular/router';

/** Each view is lazy-loaded, so claimants never download the staff screens and vice versa. */
export const routes: Routes = [
  { path: '', redirectTo: 'staff', pathMatch: 'full' },
  {
    path: 'staff',
    loadComponent: () => import('./staff/work-queue/work-queue').then((m) => m.WorkQueue),
  },
  {
    path: 'claimant',
    loadComponent: () => import('./claimant/my-claims/my-claims').then((m) => m.MyClaims),
  },
  { path: '**', redirectTo: 'staff' },
];
