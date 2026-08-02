import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { authInterceptor, OidcSecurityService } from 'angular-auth-oidc-client';
import { firstValueFrom } from 'rxjs';
import {
  provideKeycloakAuth,
  correlationIdInterceptor,
  errorInterceptor,
  KycApiClient,
  AmlApiClient,
  DocumentApiClient,
  InvestmentApiClient,
  PaymentApiClient,
  ReportingApiClient,
} from 'shared';

import { routes } from './app.routes';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideAnimationsAsync(),
    provideRouter(routes),
    // Order matters: errorInterceptor is outermost so it sees failures from
    // every interceptor after it (and the request itself), not just its own.
    provideHttpClient(
      withInterceptors([
        errorInterceptor,
        authInterceptor(),
        correlationIdInterceptor([environment.apiBaseUrl]),
      ]),
    ),
    provideKeycloakAuth({
      issuer: environment.keycloak.issuer,
      clientId: environment.keycloak.clientId,
      appOrigin: window.location.origin,
      secureRoutes: [environment.apiBaseUrl],
      production: environment.production,
    }),
    // Blocks bootstrap (and therefore the router's initial navigation and
    // every route guard) until checkAuth() has processed the OIDC redirect
    // callback and populated storage. Without this, roleGuard/
    // defaultRedirectGuard can run — and CurrentUserService's roles/subjectId
    // signals can resolve their one-shot getPayloadFromAccessToken() read —
    // BEFORE the access token exists, permanently freezing them at their
    // empty initial value (a subsequent reload "fixes" it only because the
    // token is by then already in storage). Client Portal doesn't need this:
    // it has no route guards, and its own components only read
    // CurrentUserService after app.ts's isAuthenticated() gate is already
    // true. App's own checkAuth() call in ngOnInit still runs after this and
    // is safe/idempotent — it just confirms the already-resolved state to
    // drive the loading-spinner UI.
    provideAppInitializer(() => firstValueFrom(inject(OidcSecurityService).checkAuth())),
    // All six route through the Gateway (guide §12.1) — same base URL as
    // the bearer-token/correlation-ID scoping above, not each service's own
    // direct address.
    KycApiClient.provideApi(environment.apiBaseUrl),
    AmlApiClient.provideApi(environment.apiBaseUrl),
    DocumentApiClient.provideApi(environment.apiBaseUrl),
    InvestmentApiClient.provideApi(environment.apiBaseUrl),
    PaymentApiClient.provideApi(environment.apiBaseUrl),
    ReportingApiClient.provideApi(environment.apiBaseUrl),
  ],
};
