import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { CurrentUserService, PaymentApiClient, ReportingApiClient } from 'shared';
import { Payments } from './payments';

describe('Payments', () => {
  let fixture: ComponentFixture<Payments>;
  let reportsClient: { listPayments: ReturnType<typeof vi.fn> };
  let paymentsClient: { settle: ReturnType<typeof vi.fn>; fail: ReturnType<typeof vi.fn> };

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

  async function setup(roles: string[] = []) {
    reportsClient = { listPayments: vi.fn() };
    paymentsClient = { settle: vi.fn(), fail: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [Payments],
      providers: [
        { provide: CurrentUserService, useValue: { roles: signal(roles) } },
        { provide: ReportingApiClient.ReportsClient, useValue: reportsClient },
        { provide: PaymentApiClient.PaymentsClient, useValue: paymentsClient },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Payments);
  }

  it('lists payments with their amount and status', async () => {
    await setup();
    reportsClient.listPayments.mockReturnValue(
      of({ success: true, data: [payment({})], meta: null }),
    );
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('customer-1');
    expect(el.textContent).toContain('500');
    expect(el.textContent).toContain('USD');
    expect(el.textContent).toContain('PENDING');
  });

  it('shows the failure reason for a failed payment', async () => {
    await setup();
    reportsClient.listPayments.mockReturnValue(
      of({
        success: true,
        data: [
          payment({
            status: ReportingApiClient.PaymentTransferResponse.StatusEnum.Failed,
            failureReason: 'Card declined',
          }),
        ],
        meta: null,
      }),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Card declined');
  });

  it('shows an empty state when there are no payments', async () => {
    await setup();
    reportsClient.listPayments.mockReturnValue(of({ success: true, data: [], meta: null }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No payments found');
  });

  it('shows a retryable error state on failure', async () => {
    await setup();
    reportsClient.listPayments.mockReturnValue(throwError(() => new Error('network error')));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('lib-error-state')).toBeTruthy();
  });

  it('does not show decision controls for a non-operations role', async () => {
    await setup(['auditor']);
    reportsClient.listPayments.mockReturnValue(
      of({ success: true, data: [payment({})], meta: null }),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.decision-form')).toBeNull();
  });

  it('lets operations settle a pending payment', async () => {
    await setup(['operations']);
    reportsClient.listPayments.mockReturnValue(
      of({ success: true, data: [payment({})], meta: null }),
    );
    paymentsClient.settle.mockReturnValue(of({ success: true }));
    fixture.detectChanges();

    const settleButton = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b) => (b as HTMLButtonElement).textContent?.trim() === 'Settle',
    ) as HTMLButtonElement;
    settleButton.click();

    expect(paymentsClient.settle).toHaveBeenCalledWith('transfer-1');
  });
});
