import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { KycApiClient, ReportingApiClient } from 'shared';
import { KycReviewStore } from './kyc-review.store';

describe('KycReviewStore', () => {
  let reportsClient: { listKycChecks: ReturnType<typeof vi.fn> };
  let kycChecksClient: { decide: ReturnType<typeof vi.fn> };

  function check(
    overrides: Partial<ReportingApiClient.KycCheckReportResponse>,
  ): ReportingApiClient.KycCheckReportResponse {
    return {
      checkId: 'check-1',
      customerId: 'customer-1',
      status: ReportingApiClient.KycCheckReportResponse.StatusEnum.Pending,
      requestedAt: '2026-08-01T00:00:00Z',
      ...overrides,
    };
  }

  function setup() {
    reportsClient = { listKycChecks: vi.fn() };
    kycChecksClient = { decide: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        KycReviewStore,
        { provide: ReportingApiClient.ReportsClient, useValue: reportsClient },
        { provide: KycApiClient.KYCChecksClient, useValue: kycChecksClient },
      ],
    });
    return TestBed.inject(KycReviewStore);
  }

  it('loads checks from Reporting Service into the queue', () => {
    const store = setup();
    reportsClient.listKycChecks.mockReturnValue(
      of({ success: true, data: [check({})], meta: null }),
    );

    store.load();

    expect(reportsClient.listKycChecks).toHaveBeenCalledWith(undefined, 0, 200);
    expect(store.items()).toEqual([check({})]);
    expect(store.loading()).toBe(false);
  });

  it('sets loadError when the read fails', () => {
    const store = setup();
    reportsClient.listKycChecks.mockReturnValue(throwError(() => new Error('network error')));

    store.load();

    expect(store.loadError()).toBe(true);
  });

  it('decide delegates to KYCChecksClient with the outcome and reason', () => {
    const store = setup();
    kycChecksClient.decide.mockReturnValue(of({ success: true }));

    store.decide('check-1', 'APPROVED', 'Docs verified').subscribe();

    expect(kycChecksClient.decide).toHaveBeenCalledWith('check-1', {
      outcome: 'APPROVED',
      reason: 'Docs verified',
    });
  });
});
