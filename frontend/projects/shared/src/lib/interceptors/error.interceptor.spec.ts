import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  HttpErrorResponse,
  HttpRequest,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { throwError } from 'rxjs';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { errorInterceptor } from './error.interceptor';
import { NotificationService } from '../notifications/notification.service';

describe('errorInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let notificationService: { showError: ReturnType<typeof vi.fn> };
  let oidcSecurityService: { authorize: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    notificationService = { showError: vi.fn() };
    oidcSecurityService = { authorize: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: notificationService },
        { provide: OidcSecurityService, useValue: oidcSecurityService },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function triggerError(
    status: number,
    statusText: string,
    body: Record<string, unknown> = {},
  ): unknown {
    let caught: unknown;
    httpClient.get('/api/test').subscribe({ error: (err: unknown) => (caught = err) });
    httpMock.expectOne('/api/test').flush(body, { status, statusText });
    return caught;
  }

  it('shows a connectivity message and does not re-authorize on a network failure (status 0)', () => {
    const caught = triggerError(0, 'Unknown Error');

    expect(notificationService.showError).toHaveBeenCalledWith(
      'Cannot reach the server. Check your connection and try again.',
    );
    expect(oidcSecurityService.authorize).not.toHaveBeenCalled();
    expect(caught).toBeInstanceOf(HttpErrorResponse);
  });

  it('re-authorizes without showing a notification on 401', () => {
    triggerError(401, 'Unauthorized');

    expect(oidcSecurityService.authorize).toHaveBeenCalledOnce();
    expect(notificationService.showError).not.toHaveBeenCalled();
  });

  it('shows a permission message on 403 without re-authorizing', () => {
    triggerError(403, 'Forbidden');

    expect(notificationService.showError).toHaveBeenCalledWith(
      "You don't have permission to do that.",
    );
    expect(oidcSecurityService.authorize).not.toHaveBeenCalled();
  });

  it('shows the RFC 7807 problem detail message for other error statuses', () => {
    triggerError(400, 'Bad Request', { detail: 'Amount must be positive.' });

    expect(notificationService.showError).toHaveBeenCalledWith('Amount must be positive.');
  });

  it('falls back to a generic message when the problem body has no detail', () => {
    triggerError(500, 'Internal Server Error', {});

    expect(notificationService.showError).toHaveBeenCalledWith(
      'Something went wrong. Please try again.',
    );
  });

  it('re-throws the original error so a caller can still add its own handling', () => {
    const caught = triggerError(400, 'Bad Request', { detail: 'x' });

    expect(caught).toBeInstanceOf(HttpErrorResponse);
    expect((caught as HttpErrorResponse).status).toBe(400);
  });

  it('shows a generic message when the caught error is not an HttpErrorResponse', () => {
    const req = new HttpRequest('GET', '/api/test');
    let caught: unknown;

    TestBed.runInInjectionContext(() => {
      errorInterceptor(req, () => throwError(() => new Error('boom'))).subscribe({
        error: (err: unknown) => (caught = err),
      });
    });

    expect(notificationService.showError).toHaveBeenCalledWith(
      'An unexpected error occurred. Please try again.',
    );
    expect((caught as Error).message).toBe('boom');
  });
});
