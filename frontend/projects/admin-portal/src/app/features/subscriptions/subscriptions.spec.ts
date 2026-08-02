import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { CurrentUserService, InvestmentApiClient, ReportingApiClient } from 'shared';
import { Subscriptions } from './subscriptions';

describe('Subscriptions', () => {
  let fixture: ComponentFixture<Subscriptions>;
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

  async function setup(roles: string[] = []) {
    reportsClient = { listSubscriptions: vi.fn() };
    subscriptionsClient = { confirmPayment: vi.fn(), cancel: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [Subscriptions],
      providers: [
        { provide: CurrentUserService, useValue: { roles: signal(roles) } },
        { provide: ReportingApiClient.ReportsClient, useValue: reportsClient },
        { provide: InvestmentApiClient.SubscriptionsClient, useValue: subscriptionsClient },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Subscriptions);
  }

  it('lists subscriptions with their status', async () => {
    await setup();
    reportsClient.listSubscriptions.mockReturnValue(
      of({ success: true, data: [subscription({})], meta: null }),
    );
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('customer-1');
    expect(el.textContent).toContain('GLOBAL-EQUITY-01');
    expect(el.textContent).toContain('AWAITING_PAYMENT');
  });

  it('shows the failure reason for a failed subscription', async () => {
    await setup();
    reportsClient.listSubscriptions.mockReturnValue(
      of({
        success: true,
        data: [
          subscription({
            status: ReportingApiClient.SubscriptionReportResponse.StatusEnum.Failed,
            failureReason: 'KYC not approved',
          }),
        ],
        meta: null,
      }),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('KYC not approved');
  });

  it('shows an empty state when there are no subscriptions', async () => {
    await setup();
    reportsClient.listSubscriptions.mockReturnValue(of({ success: true, data: [], meta: null }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No subscriptions found');
  });

  it('shows a retryable error state on failure', async () => {
    await setup();
    reportsClient.listSubscriptions.mockReturnValue(throwError(() => new Error('network error')));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('lib-error-state')).toBeTruthy();
  });

  it('does not show action buttons for a non-managing role', async () => {
    await setup(['auditor']);
    reportsClient.listSubscriptions.mockReturnValue(
      of({ success: true, data: [subscription({})], meta: null }),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.decision-form')).toBeNull();
  });

  it('lets an operations user confirm payment', async () => {
    await setup(['operations']);
    reportsClient.listSubscriptions.mockReturnValue(
      of({ success: true, data: [subscription({})], meta: null }),
    );
    subscriptionsClient.confirmPayment.mockReturnValue(of({ success: true }));
    fixture.detectChanges();

    const confirmButton = Array.from(fixture.nativeElement.querySelectorAll('button')).find((b) =>
      (b as HTMLButtonElement).textContent?.includes('Confirm Payment'),
    ) as HTMLButtonElement;
    confirmButton.click();

    expect(subscriptionsClient.confirmPayment).toHaveBeenCalledWith('sub-1');
  });
});
