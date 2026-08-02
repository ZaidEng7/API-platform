import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { CurrentUserService, KycApiClient, ReportingApiClient } from 'shared';
import { KycReview } from './kyc-review';

describe('KycReview', () => {
  let fixture: ComponentFixture<KycReview>;
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

  async function setup(roles: string[] = []) {
    reportsClient = { listKycChecks: vi.fn() };
    kycChecksClient = { decide: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [KycReview],
      providers: [
        { provide: CurrentUserService, useValue: { roles: signal(roles) } },
        { provide: ReportingApiClient.ReportsClient, useValue: reportsClient },
        { provide: KycApiClient.KYCChecksClient, useValue: kycChecksClient },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(KycReview);
  }

  it('lists KYC checks with their status', async () => {
    await setup();
    reportsClient.listKycChecks.mockReturnValue(
      of({ success: true, data: [check({})], meta: null }),
    );
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('customer-1');
    expect(el.textContent).toContain('PENDING');
  });

  it('shows an empty state when there are no checks', async () => {
    await setup();
    reportsClient.listKycChecks.mockReturnValue(of({ success: true, data: [], meta: null }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No KYC checks found');
  });

  it('shows a retryable error state on failure', async () => {
    await setup();
    reportsClient.listKycChecks.mockReturnValue(throwError(() => new Error('network error')));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('lib-error-state')).toBeTruthy();
  });

  it('does not show a decision form for a non-compliance role', async () => {
    await setup(['operations']);
    reportsClient.listKycChecks.mockReturnValue(
      of({ success: true, data: [check({})], meta: null }),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.decision-form')).toBeNull();
  });

  it('lets a compliance user approve a pending check with a reason', async () => {
    await setup(['compliance']);
    reportsClient.listKycChecks.mockReturnValue(
      of({ success: true, data: [check({})], meta: null }),
    );
    kycChecksClient.decide.mockReturnValue(of({ success: true }));
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const input = el.querySelector('.reason-input') as HTMLInputElement;
    input.value = 'Docs verified';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const approveButton = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Approve'),
    ) as HTMLButtonElement;
    expect(approveButton.disabled).toBe(false);
    approveButton.click();

    expect(kycChecksClient.decide).toHaveBeenCalledWith('check-1', {
      outcome: 'APPROVED',
      reason: 'Docs verified',
    });
    expect(reportsClient.listKycChecks).toHaveBeenCalledTimes(2); // initial load + reload after decide
  });

  it('disables the decision buttons until a reason is entered', async () => {
    await setup(['compliance']);
    reportsClient.listKycChecks.mockReturnValue(
      of({ success: true, data: [check({})], meta: null }),
    );
    fixture.detectChanges();

    const approveButton = Array.from(fixture.nativeElement.querySelectorAll('button')).find((b) =>
      (b as HTMLButtonElement).textContent?.includes('Approve'),
    ) as HTMLButtonElement;
    expect(approveButton.disabled).toBe(true);
  });
});
