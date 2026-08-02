import { inject } from '@angular/core';
import type { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { NotificationService } from '../notifications/notification.service';
import type { ProblemDetail } from '../error-handling/problem-detail';

/**
 * Maps every backend RFC 7807 `problem+json` error response to a
 * notification, and handles the two auth-specific status codes
 * specially (guide §11.2 error contract; guide §12 — a 401 means the
 * session is no longer valid, not that this one request should just be
 * retried). Re-throws in all cases so a specific component can still add
 * its own handling (e.g. an inline form error) on top of the notification.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notificationService = inject(NotificationService);
  const oidcSecurityService = inject(OidcSecurityService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (isHttpErrorResponse(error)) {
        handleHttpError(error, notificationService, oidcSecurityService);
      } else {
        notificationService.showError('An unexpected error occurred. Please try again.');
      }
      return throwError(() => error);
    }),
  );
};

function handleHttpError(
  error: HttpErrorResponse,
  notificationService: NotificationService,
  oidcSecurityService: OidcSecurityService,
): void {
  if (error.status === 0) {
    notificationService.showError('Cannot reach the server. Check your connection and try again.');
    return;
  }

  if (error.status === 401) {
    // The session is no longer valid (expired/revoked) — re-authenticate
    // rather than showing an error the user can't act on.
    oidcSecurityService.authorize();
    return;
  }

  if (error.status === 403) {
    notificationService.showError("You don't have permission to do that.");
    return;
  }

  const problem = error.error as ProblemDetail | undefined;
  notificationService.showError(problem?.detail ?? 'Something went wrong. Please try again.');
}

function isHttpErrorResponse(error: unknown): error is HttpErrorResponse {
  return typeof error === 'object' && error !== null && 'status' in error && 'error' in error;
}
