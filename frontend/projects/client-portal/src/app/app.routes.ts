import { Routes } from '@angular/router';

// No per-route auth guard is needed here — App (app.ts) already gates the
// entire UI behind isAuthenticated() before the router-outlet is ever
// rendered, since every Client Portal view is investor-only (unlike Admin
// Portal, where different sections may end up gated to different staff
// roles).
export const routes: Routes = [
  {
    path: 'portfolio',
    loadComponent: () => import('./features/my-portfolio/my-portfolio').then((m) => m.MyPortfolio),
  },
  {
    path: 'subscriptions',
    loadComponent: () =>
      import('./features/my-subscriptions/my-subscriptions').then((m) => m.MySubscriptions),
  },
  {
    path: 'compliance-status',
    loadComponent: () =>
      import('./features/compliance-status/compliance-status').then((m) => m.ComplianceStatus),
  },
  {
    path: 'party-lookup',
    loadComponent: () => import('./features/party-lookup/party-lookup').then((m) => m.PartyLookup),
  },
  { path: '', pathMatch: 'full', redirectTo: 'portfolio' },
  { path: '**', redirectTo: 'portfolio' },
];
