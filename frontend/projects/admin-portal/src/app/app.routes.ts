import { Routes } from '@angular/router';
import { roleGuard } from './core/role.guard';
import { defaultRedirectGuard } from './core/default-redirect.guard';
import { COMPLIANCE_REVIEW_ROLES, SUBSCRIPTIONS_AND_PAYMENTS_ROLES } from './core/roles';

// Unlike Client Portal (whole UI gated behind isAuthenticated() once, in
// app.ts), Admin Portal's sections are gated to different staff roles —
// each route below carries its own roleGuard so a signed-in but
// under-privileged staff user (e.g. an auditor hitting /subscriptions
// directly) is redirected rather than shown a broken page.
export const routes: Routes = [
  {
    path: 'kyc-review',
    canActivate: [roleGuard(COMPLIANCE_REVIEW_ROLES)],
    loadComponent: () => import('./features/kyc-review/kyc-review').then((m) => m.KycReview),
  },
  {
    path: 'aml-review',
    canActivate: [roleGuard(COMPLIANCE_REVIEW_ROLES)],
    loadComponent: () => import('./features/aml-review/aml-review').then((m) => m.AmlReview),
  },
  {
    path: 'document-review',
    canActivate: [roleGuard(COMPLIANCE_REVIEW_ROLES)],
    loadComponent: () =>
      import('./features/document-review/document-review').then((m) => m.DocumentReview),
  },
  {
    path: 'subscriptions',
    canActivate: [roleGuard(SUBSCRIPTIONS_AND_PAYMENTS_ROLES)],
    loadComponent: () =>
      import('./features/subscriptions/subscriptions').then((m) => m.Subscriptions),
  },
  {
    path: 'payments',
    canActivate: [roleGuard(SUBSCRIPTIONS_AND_PAYMENTS_ROLES)],
    loadComponent: () => import('./features/payments/payments').then((m) => m.Payments),
  },
  {
    path: 'access-denied',
    loadComponent: () =>
      import('./features/access-denied/access-denied').then((m) => m.AccessDenied),
  },
  // Never actually renders AccessDenied — defaultRedirectGuard always
  // returns a UrlTree, redirecting before this route's component would
  // activate. It's only here to satisfy Routes' typing (a route needs a
  // component or redirectTo).
  {
    path: '',
    pathMatch: 'full',
    canActivate: [defaultRedirectGuard],
    loadComponent: () =>
      import('./features/access-denied/access-denied').then((m) => m.AccessDenied),
  },
  { path: '**', redirectTo: '' },
];
