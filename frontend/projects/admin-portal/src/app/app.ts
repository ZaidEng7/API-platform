import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import {
  AppShell,
  CurrentUserService,
  LoadingSpinner,
  OidcSecurityService,
  type NavLink,
} from 'shared';
import { COMPLIANCE_REVIEW_ROLES, SUBSCRIPTIONS_AND_PAYMENTS_ROLES } from './core/roles';

interface RoleGatedNavLink extends NavLink {
  readonly allowedRoles: readonly string[];
}

const ALL_NAV_LINKS: readonly RoleGatedNavLink[] = [
  {
    label: 'KYC Review',
    path: '/kyc-review',
    icon: 'fact_check',
    allowedRoles: COMPLIANCE_REVIEW_ROLES,
  },
  {
    label: 'AML Review',
    path: '/aml-review',
    icon: 'gavel',
    allowedRoles: COMPLIANCE_REVIEW_ROLES,
  },
  {
    label: 'Document Review',
    path: '/document-review',
    icon: 'description',
    allowedRoles: COMPLIANCE_REVIEW_ROLES,
  },
  {
    label: 'Subscriptions',
    path: '/subscriptions',
    icon: 'receipt_long',
    allowedRoles: SUBSCRIPTIONS_AND_PAYMENTS_ROLES,
  },
  {
    label: 'Payments',
    path: '/payments',
    icon: 'payments',
    allowedRoles: SUBSCRIPTIONS_AND_PAYMENTS_ROLES,
  },
];

/**
 * Root shell: resolves the OIDC session on load, shows a sign-in screen
 * when unauthenticated, otherwise wraps the routed pages in the shared
 * {@link AppShell}. `navLinks` is filtered per the signed-in user's own
 * realm roles (see core/roles.ts) — every route it points at is also
 * independently guarded by roleGuard, so a stale/cached nav can never grant
 * access the route itself wouldn't.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AppShell, LoadingSpinner, MatButtonModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  private readonly oidcSecurityService = inject(OidcSecurityService);
  private readonly currentUserService = inject(CurrentUserService);

  protected readonly navLinks = computed<NavLink[]>(() => {
    const roles = this.currentUserService.roles();
    return ALL_NAV_LINKS.filter((link) => link.allowedRoles.some((role) => roles.includes(role)));
  });
  protected readonly checkingAuth = signal(true);
  protected readonly isAuthenticated = signal(false);
  protected readonly userDisplayName = signal<string | null>(null);

  ngOnInit(): void {
    this.oidcSecurityService.checkAuth().subscribe(({ isAuthenticated, userData }) => {
      this.isAuthenticated.set(isAuthenticated);
      this.userDisplayName.set(
        (userData?.['preferred_username'] as string | undefined) ??
          (userData?.['name'] as string | undefined) ??
          null,
      );
      this.checkingAuth.set(false);
    });
  }

  protected login(): void {
    this.oidcSecurityService.authorize();
  }

  protected logout(): void {
    this.oidcSecurityService.logoff().subscribe();
  }
}
