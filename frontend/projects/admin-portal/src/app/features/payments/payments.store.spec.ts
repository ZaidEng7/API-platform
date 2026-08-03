import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PaymentApiClient, ReportingApiClient } from 'shared';
import { PaymentsStore } from './payments.store';

describe('PaymentsStore', () => {
  let reportsClient: { listPayments: ReturnType<typeof vi.fn> };
  let paymentsClient: {
    settle: ReturnType<typeof vi.fn>;
    fail: ReturnType<typeof vi.fn>;
    requestTransfer: ReturnType<typeof vi.fn>;
  };

  function payment(
    overrides: Partial<ReportingApiClient.PaymentTransferResponse>,
  ): ReportingApiClient.PaymentTransferResponse {
    return {
      transferId: 'transfer-1',
      customerId: 'customer-1',
      amount: 500,
      currency: 'USD',
      status: ReportingApiClient.PaymentTransferResponse.StatusEnum.Pending,
      requestedAt: '2026-08-01T00:00:00Z',
      ...overrides,
    };
  }

  function setup() {
    reportsClient = { listPayments: vi.fn() };
    paymentsClient = { settle: vi.fn(), fail: vi.fn(), requestTransfer: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        PaymentsStore,
        { provide: ReportingApiClient.ReportsClient, useValue: reportsClient },
        { provide: PaymentApiClient.PaymentsClient, useValue: paymentsClient },
      ],
    });
    return TestBed.inject(PaymentsStore);
  }

  it('loads payments from Reporting Service into the queue', () => {
    const store = setup();
    reportsClient.listPayments.mockReturnValue(
      of({ success: true, data: [payment({})], meta: null }),
    );

    store.load();

    expect(reportsClient.listPayments).toHaveBeenCalledWith(undefined, 0, 200);
    expect(store.items()).toEqual([payment({})]);
  });

  it('sets loadError when the read fails', () => {
    const store = setup();
    reportsClient.listPayments.mockReturnValue(throwError(() => new Error('network error')));

    store.load();

    expect(store.loadError()).toBe(true);
  });

  it('settle delegates to PaymentsClient', () => {
    const store = setup();
    paymentsClient.settle.mockReturnValue(of({ success: true }));

    store.settle('transfer-1').subscribe();

    expect(paymentsClient.settle).toHaveBeenCalledWith('transfer-1');
  });

  it('fail delegates to PaymentsClient with a reason', () => {
    const store = setup();
    paymentsClient.fail.mockReturnValue(of({ success: true }));

    store.fail('transfer-1', 'Card declined').subscribe();

    expect(paymentsClient.fail).toHaveBeenCalledWith('transfer-1', { reason: 'Card declined' });
  });

  it('create delegates to PaymentsClient with a fresh idempotency key', () => {
    const store = setup();
    paymentsClient.requestTransfer.mockReturnValue(of({ success: true }));
    const request: PaymentApiClient.RequestTransferRequest = {
      customerId: 'customer-1',
      ownerId: 'owner-1',
      amount: 500,
      currency: 'USD',
      paymentMethodToken: 'tok_test',
    };

    store.create(request).subscribe();

    expect(paymentsClient.requestTransfer).toHaveBeenCalledWith(request, expect.any(String));
  });
});
