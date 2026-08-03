import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { CurrentUserService, PaymentApiClient, ReportingApiClient } from 'shared';
import { Payments } from './payments';

describe('Payments', () => {
  let fixture: ComponentFixture<Payments>;
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

  async function setup(roles: string[] = []) {
    reportsClient = { listPayments: vi.fn() };
    paymentsClient = { settle: vi.fn(), fail: vi.fn(), requestTransfer: vi.fn() };

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

  it('does not show decision controls or the new-payment form for a non-managing role', async () => {
    await setup(['auditor']);
    reportsClient.listPayments.mockReturnValue(
      of({ success: true, data: [payment({})], meta: null }),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.decision-form')).toBeNull();
    expect(fixture.nativeElement.querySelector('.new-payment-form')).toBeNull();
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

  it('lets a portfolio-manager user request a new payment and reloads the list', async () => {
    await setup(['portfolio-manager']);
    reportsClient.listPayments.mockReturnValue(of({ success: true, data: [], meta: null }));
    paymentsClient.requestTransfer.mockReturnValue(of({ success: true }));
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const inputs = Array.from(el.querySelectorAll('.new-payment-form input')) as HTMLInputElement[];
    const [customerIdInput, ownerIdInput, amountInput, currencyInput, tokenInput, referenceInput] =
      inputs;
    const setter = Object.getOwnPropertyDescriptor(
      window.HTMLInputElement.prototype,
      'value',
    )!.set!;
    setter.call(customerIdInput, 'customer-1');
    customerIdInput.dispatchEvent(new Event('input', { bubbles: true }));
    setter.call(ownerIdInput, 'owner-1');
    ownerIdInput.dispatchEvent(new Event('input', { bubbles: true }));
    setter.call(amountInput, '500');
    amountInput.dispatchEvent(new Event('input', { bubbles: true }));
    setter.call(currencyInput, 'USD');
    currencyInput.dispatchEvent(new Event('input', { bubbles: true }));
    setter.call(tokenInput, 'tok_test');
    tokenInput.dispatchEvent(new Event('input', { bubbles: true }));
    setter.call(referenceInput, 'sub-1');
    referenceInput.dispatchEvent(new Event('input', { bubbles: true }));

    el.querySelector('.new-payment-form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    expect(paymentsClient.requestTransfer).toHaveBeenCalledWith(
      {
        customerId: 'customer-1',
        ownerId: 'owner-1',
        amount: 500,
        currency: 'USD',
        paymentMethodToken: 'tok_test',
        reference: 'sub-1',
      },
      expect.any(String),
    );
    expect(reportsClient.listPayments).toHaveBeenCalledTimes(2);
  });

  it('does not submit the new-payment form with missing fields', async () => {
    await setup(['operations']);
    reportsClient.listPayments.mockReturnValue(of({ success: true, data: [], meta: null }));
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('.new-payment-form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    expect(paymentsClient.requestTransfer).not.toHaveBeenCalled();
  });
});
