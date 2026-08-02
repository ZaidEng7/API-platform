/*
 * Public API Surface of shared
 */

export * from './lib/layout/app-shell/app-shell';
export * from './lib/layout/loading-spinner/loading-spinner';
export * from './lib/layout/error-state/error-state';
export * from './lib/auth/auth.config';
export * from './lib/interceptors/correlation-id.interceptor';
export * from './lib/interceptors/error.interceptor';
export * from './lib/notifications/notification.service';
export * from './lib/error-handling/problem-detail';
export { OidcSecurityService, autoLoginPartialRoutesGuard } from 'angular-auth-oidc-client';

// Generated API clients (openapi-generator, see scripts/generate-api-client.sh).
// Namespaced per service: each generated client has its own Configuration/BASE_PATH/
// provideApi/APIS symbols that would collide if re-exported flat once more services
// are generated here.
export * as GatewayApiClient from './lib/generated-api/gateway';
export * as PortfolioApiClient from './lib/generated-api/portfolio-service';
export * as InvestmentApiClient from './lib/generated-api/investment-service';
export * as KycApiClient from './lib/generated-api/kyc-service';
export * as AmlApiClient from './lib/generated-api/aml-service';
export * as DocumentApiClient from './lib/generated-api/document-service';
export * as PaymentApiClient from './lib/generated-api/payment-service';
export * as ReportingApiClient from './lib/generated-api/reporting-service';

export * from './lib/current-user/current-user.service';
