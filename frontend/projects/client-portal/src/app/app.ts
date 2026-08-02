import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { AppShell, LoadingSpinner, OidcSecurityService, type NavLink } from 'shared';

/**
 * Root shell: resolves the OIDC session on load, shows a sign-in screen
 * when unauthenticated, otherwise wraps the routed pages in the shared
 * {@link AppShell}. Feature nav links are added here as each one lands
 * (Phase B) — deliberately empty for now rather than linking to routes
 * that don't exist yet.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AppShell, LoadingSpinner, MatButtonModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  private readonly oidcSecurityService = inject(OidcSecurityService);

  protected readonly navLinks: NavLink[] = [];
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
