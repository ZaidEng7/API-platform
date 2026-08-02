import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { correlationIdInterceptor, CORRELATION_ID_HEADER } from './correlation-id.interceptor';

describe('correlationIdInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;

  const apiBaseUrl = 'http://localhost:8080';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([correlationIdInterceptor([apiBaseUrl])])),
        provideHttpClientTesting(),
      ],
    });
    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('attaches a correlation id header matching a UUID to requests bound for our own backend', () => {
    httpClient.get(`${apiBaseUrl}/api/test`).subscribe();

    const req = httpMock.expectOne(`${apiBaseUrl}/api/test`);
    const headerValue = req.request.headers.get(CORRELATION_ID_HEADER);

    expect(headerValue).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);

    req.flush({});
  });

  it('generates a different id for each request', () => {
    httpClient.get(`${apiBaseUrl}/api/one`).subscribe();
    httpClient.get(`${apiBaseUrl}/api/two`).subscribe();

    const first = httpMock.expectOne(`${apiBaseUrl}/api/one`);
    const second = httpMock.expectOne(`${apiBaseUrl}/api/two`);
    const idOne = first.request.headers.get(CORRELATION_ID_HEADER);
    const idTwo = second.request.headers.get(CORRELATION_ID_HEADER);

    expect(idOne).not.toEqual(idTwo);

    first.flush({});
    second.flush({});
  });

  it('does not attach the header to requests outside the configured API base URLs', () => {
    httpClient
      .get('http://localhost:8082/realms/company/.well-known/openid-configuration')
      .subscribe();

    const req = httpMock.expectOne(
      'http://localhost:8082/realms/company/.well-known/openid-configuration',
    );

    expect(req.request.headers.has(CORRELATION_ID_HEADER)).toBe(false);

    req.flush({});
  });
});
