import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { authInterceptor } from 'angular-auth-oidc-client';
import {
  provideKeycloakAuth,
  correlationIdInterceptor,
  errorInterceptor,
  PortfolioApiClient,
  InvestmentApiClient,
  KycApiClient,
  AmlApiClient,
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
    // All four route through the Gateway (guide §12.1) — same base URL as
    // the bearer-token/correlation-ID scoping above, not each service's own
    // direct address.
    PortfolioApiClient.provideApi(environment.apiBaseUrl),
    InvestmentApiClient.provideApi(environment.apiBaseUrl),
    KycApiClient.provideApi(environment.apiBaseUrl),
    AmlApiClient.provideApi(environment.apiBaseUrl),
  ],
};
