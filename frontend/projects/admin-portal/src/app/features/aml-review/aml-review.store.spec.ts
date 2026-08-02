import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AmlApiClient, ReportingApiClient } from 'shared';
import { AmlReviewStore } from './aml-review.store';

describe('AmlReviewStore', () => {
  let reportsClient: { listAmlScreenings: ReturnType<typeof vi.fn> };
  let amlScreeningsClient: {
    recordResult: ReturnType<typeof vi.fn>;
    recordFailure: ReturnType<typeof vi.fn>;
  };

  function screening(
    overrides: Partial<ReportingApiClient.AmlScreeningReportResponse>,
  ): ReportingApiClient.AmlScreeningReportResponse {
    return {
      screeningId: 'screening-1',
      customerId: 'customer-1',
      status: ReportingApiClient.AmlScreeningReportResponse.StatusEnum.InProgress,
      requestedAt: '2026-08-01T00:00:00Z',
      ...overrides,
    };
  }

  function setup() {
    reportsClient = { listAmlScreenings: vi.fn() };
    amlScreeningsClient = { recordResult: vi.fn(), recordFailure: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        AmlReviewStore,
        { provide: ReportingApiClient.ReportsClient, useValue: reportsClient },
        { provide: AmlApiClient.AMLScreeningsClient, useValue: amlScreeningsClient },
      ],
    });
    return TestBed.inject(AmlReviewStore);
  }

  it('loads screenings from Reporting Service into the queue', () => {
    const store = setup();
    reportsClient.listAmlScreenings.mockReturnValue(
      of({ success: true, data: [screening({})], meta: null }),
    );

    store.load();

    expect(reportsClient.listAmlScreenings).toHaveBeenCalledWith(undefined, 0, 200);
    expect(store.items()).toEqual([screening({})]);
  });

  it('sets loadError when the read fails', () => {
    const store = setup();
    reportsClient.listAmlScreenings.mockReturnValue(throwError(() => new Error('network error')));

    store.load();

    expect(store.loadError()).toBe(true);
  });

  it('recordResult delegates to AMLScreeningsClient with outcome and notes', () => {
    const store = setup();
    amlScreeningsClient.recordResult.mockReturnValue(of({ success: true }));

    store.recordResult('screening-1', 'CLEAR', 'Nothing found').subscribe();

    expect(amlScreeningsClient.recordResult).toHaveBeenCalledWith('screening-1', {
      outcome: 'CLEAR',
      notes: 'Nothing found',
    });
  });

  it('recordFailure delegates to AMLScreeningsClient with a reason', () => {
    const store = setup();
    amlScreeningsClient.recordFailure.mockReturnValue(of({ success: true }));

    store.recordFailure('screening-1', 'Vendor timeout').subscribe();

    expect(amlScreeningsClient.recordFailure).toHaveBeenCalledWith('screening-1', {
      reason: 'Vendor timeout',
    });
  });
});
