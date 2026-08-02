import type { HttpInterceptorFn } from '@angular/common/http';

/** Matches the backend's own header name (`CorrelationIdFilter.HEADER`, shared/common-web). */
export const CORRELATION_ID_HEADER = 'X-Correlation-Id';

/**
 * Generates a correlation id for every request to our own backend (the
 * browser is always the first hop, so there's never an upstream value to
 * propagate, unlike the Gateway's own `CorrelationIdFilter` which also
 * handles a propagate-if-present case) — lets a single user action be
 * traced through backend logs and traces end to end (guide §7, §14).
 *
 * Scoped to `apiBaseUrls` (the same list passed as OIDC `secureRoutes`) —
 * not applied to third-party requests such as the OIDC library's own calls
 * to Keycloak, whose CORS policy doesn't allowlist this custom header and
 * would otherwise reject the (now-modified) request outright.
 */
export function correlationIdInterceptor(apiBaseUrls: readonly string[]): HttpInterceptorFn {
  return (req, next) => {
    if (!apiBaseUrls.some((baseUrl) => req.url.startsWith(baseUrl))) {
      return next(req);
    }

    const correlationId = crypto.randomUUID();
    return next(
      req.clone({
        setHeaders: { [CORRELATION_ID_HEADER]: correlationId },
      }),
    );
  };
}
