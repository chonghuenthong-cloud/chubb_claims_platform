import { Routes } from '@angular/router';
import { homeRedirect, roleGuard } from './core/role.guard';

/**
 * Each view is lazy-loaded, so claimants never download the staff screens and vice versa.
 * Guards keep each role inside its own area.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', canActivate: [homeRedirect], children: [] },
  {
    path: 'staff',
    canActivate: [roleGuard('STAFF')],
    loadComponent: () => import('./staff/work-queue/work-queue').then((m) => m.WorkQueue),
  },
  {
    path: 'claimant',
    canActivate: [roleGuard('CLAIMANT')],
    children: [
      { path: '', loadComponent: () => import('./claimant/my-claims/my-claims').then((m) => m.MyClaims) },
      {
        path: 'report',
        loadComponent: () => import('./claimant/report-incident/report-incident').then((m) => m.ReportIncident),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
