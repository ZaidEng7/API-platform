import { LogLevel, provideAuth } from 'angular-auth-oidc-client';
import type { EnvironmentProviders } from '@angular/core';

export interface KeycloakAuthOptions {
  /** e.g. `http://localhost:8082/realms/company` — Keycloak realm issuer. */
  readonly issuer: string;
  /** The Keycloak client id — `gateway-portal` for every frontend app (guide §5.1/§12.1). */
  readonly clientId: string;
  /** This app's own origin, e.g. `http://localhost:4200`. Must be registered on the Keycloak client's redirectUris/webOrigins. */
  readonly appOrigin: string;
  /** API base URLs the auth interceptor should attach the bearer token to (guide §5.1: never send tokens to third-party origins). */
  readonly secureRoutes: readonly string[];
  readonly production: boolean;
}

/**
 * Wraps `angular-auth-oidc-client`'s `provideAuth` with this platform's
 * fixed Keycloak conventions (Auth Code + PKCE, S256 — matches the
 * `gateway-portal` client in `platform/identity/realm-export.json`), so
 * each app only supplies what's actually specific to it (its own origin,
 * which API base URLs it calls).
 */
export function provideKeycloakAuth(options: KeycloakAuthOptions): EnvironmentProviders {
  return provideAuth({
    config: {
      authority: options.issuer,
      clientId: options.clientId,
      redirectUrl: options.appOrigin,
      postLogoutRedirectUri: options.appOrigin,
      scope: 'openid profile',
      responseType: 'code',
      silentRenew: true,
      useRefreshToken: true,
      secureRoutes: [...options.secureRoutes],
      logLevel: options.production ? LogLevel.Warn : LogLevel.Debug,
    },
  });
}
