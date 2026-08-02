import { Component, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';

export interface NavLink {
  readonly label: string;
  readonly path: string;
  readonly icon: string;
}

/**
 * Shared toolbar + side nav shell, reused by both Client Portal and Admin
 * Portal — each app supplies its own title and nav links (different roles,
 * different sections) and projects its routed pages via `<ng-content>`.
 * Deliberately auth-agnostic: the host app owns the actual OIDC session and
 * just passes down the display name / logout callback.
 */
@Component({
  selector: 'lib-app-shell',
  imports: [
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
  ],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell {
  readonly appTitle = input.required<string>();
  readonly navLinks = input<readonly NavLink[]>([]);
  readonly userDisplayName = input<string | null>(null);
  readonly logout = output<void>();
}
