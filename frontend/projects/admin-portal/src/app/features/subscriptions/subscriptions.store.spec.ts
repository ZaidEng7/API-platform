import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { InvestmentApiClient, ReportingApiClient } from 'shared';
import { SubscriptionsStore } from './subscriptions.store';

describe('SubscriptionsStore', () => {
  let reportsClient: { listSubscriptions: ReturnType<typeof vi.fn> };
  let subscriptionsClient: {
    confirmPayment: ReturnType<typeof vi.fn>;
    cancel: ReturnType<typeof vi.fn>;
  };

  function subscription(
    overrides: Partial<ReportingApiClient.SubscriptionReportResponse>,
  ): ReportingApiClient.SubscriptionReportResponse {
    return {
      subscriptionId: 'sub-1',
      customerId: 'customer-1',
      fundCode: 'GLOBAL-EQUITY-01',
      quantity: 10,
      status: ReportingApiClient.SubscriptionReportResponse.StatusEnum.AwaitingPayment,
      ...overrides,
    };
  }

  function setup() {
    reportsClient = { listSubscriptions: vi.fn() };
    subscriptionsClient = { confirmPayment: vi.fn(), cancel: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        SubscriptionsStore,
        { provide: ReportingApiClient.ReportsClient, useValue: reportsClient },
        { provide: InvestmentApiClient.SubscriptionsClient, useValue: subscriptionsClient },
      ],
    });
    return TestBed.inject(SubscriptionsStore);
  }

  it('loads subscriptions from Reporting Service into the queue', () => {
    const store = setup();
    reportsClient.listSubscriptions.mockReturnValue(
      of({ success: true, data: [subscription({})], meta: null }),
    );

    store.load();

    expect(reportsClient.listSubscriptions).toHaveBeenCalledWith(undefined, 0, 200);
    expect(store.items()).toEqual([subscription({})]);
  });

  it('sets loadError when the read fails', () => {
    const store = setup();
    reportsClient.listSubscriptions.mockReturnValue(throwError(() => new Error('network error')));

    store.load();

    expect(store.loadError()).toBe(true);
  });

  it('confirmPayment delegates to SubscriptionsClient', () => {
    const store = setup();
    subscriptionsClient.confirmPayment.mockReturnValue(of({ success: true }));

    store.confirmPayment('sub-1').subscribe();

    expect(subscriptionsClient.confirmPayment).toHaveBeenCalledWith('sub-1');
  });

  it('cancel delegates to SubscriptionsClient', () => {
    const store = setup();
    subscriptionsClient.cancel.mockReturnValue(of({ success: true }));

    store.cancel('sub-1').subscribe();

    expect(subscriptionsClient.cancel).toHaveBeenCalledWith('sub-1');
  });
});
