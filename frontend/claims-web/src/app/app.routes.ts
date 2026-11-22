import { Routes } from '@angular/router';
import { homeRedirect, roleGuard } from './core/role.guard';

/**
 * Each view is lazy-loaded, so claimants never download the staff screens and vice versa.
 * Guards keep each role inside its own area. ":id" params are bound to component inputs (withComponentInputBinding).
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', canActivate: [homeRedirect], children: [] },
  {
    path: 'staff',
    canActivate: [roleGuard('STAFF')],
    children: [
      { path: '', loadComponent: () => import('./staff/work-queue/work-queue').then((m) => m.WorkQueue) },
      {
        path: 'claims/:id',
        loadComponent: () => import('./staff/claim-detail/claim-detail').then((m) => m.ClaimDetail),
      },
    ],
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
      {
        path: 'claims/:id',
        loadComponent: () => import('./claimant/answer-info/answer-info').then((m) => m.AnswerInfo),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
