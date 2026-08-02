import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { CurrentUserService, InvestmentApiClient } from 'shared';
import { MySubscriptions } from './my-subscriptions';

describe('MySubscriptions', () => {
  let fixture: ComponentFixture<MySubscriptions>;
  let subscriptionsClient: { listByOwner: ReturnType<typeof vi.fn> };
  let currentUserService: { subjectId: ReturnType<typeof signal<string | null>> };

  const ownerId = 'a1b2c3d4-0000-0000-0000-000000000000';

  function subscription(
    overrides: Partial<InvestmentApiClient.SubscriptionResponse>,
  ): InvestmentApiClient.SubscriptionResponse {
    return {
      id: 'sub-1',
      customerId: ownerId,
      ownerId,
      portfolioId: 'portfolio-1',
      fundCode: 'GLOBAL-EQUITY-01',
      quantity: 25,
      status: InvestmentApiClient.SubscriptionResponse.StatusEnum.AwaitingPayment,
      ...overrides,
    };
  }

  async function setup(subjectId: string | null = ownerId) {
    subscriptionsClient = { listByOwner: vi.fn() };
    currentUserService = { subjectId: signal(subjectId) };

    await TestBed.configureTestingModule({
      imports: [MySubscriptions],
      providers: [
        { provide: CurrentUserService, useValue: currentUserService },
        { provide: InvestmentApiClient.SubscriptionsClient, useValue: subscriptionsClient },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MySubscriptions);
  }

  it('shows the list of subscriptions with their status', async () => {
    await setup();
    subscriptionsClient.listByOwner.mockReturnValue(
      of({ success: true, data: [subscription({})], meta: null }),
    );
    fixture.detectChanges();

    expect(subscriptionsClient.listByOwner).toHaveBeenCalledWith(ownerId);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('GLOBAL-EQUITY-01');
    expect(el.textContent).toContain('AWAITING_PAYMENT');
  });

  it('shows the failure reason for a failed subscription', async () => {
    await setup();
    subscriptionsClient.listByOwner.mockReturnValue(
      of({
        success: true,
        data: [
          subscription({
            status: InvestmentApiClient.SubscriptionResponse.StatusEnum.Failed,
            failureReason: 'KYC not approved and AML screening not clear',
          }),
        ],
        meta: null,
      }),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'KYC not approved and AML screening not clear',
    );
  });

  it('shows an empty state when there are no subscriptions', async () => {
    await setup();
    subscriptionsClient.listByOwner.mockReturnValue(of({ success: true, data: [], meta: null }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain("don't have any fund subscriptions");
  });

  it('shows a retryable error state on failure', async () => {
    await setup();
    subscriptionsClient.listByOwner.mockReturnValue(throwError(() => new Error('network error')));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('lib-error-state')).toBeTruthy();

    subscriptionsClient.listByOwner.mockReturnValue(
      of({ success: true, data: [subscription({})], meta: null }),
    );
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('GLOBAL-EQUITY-01');
  });
});
