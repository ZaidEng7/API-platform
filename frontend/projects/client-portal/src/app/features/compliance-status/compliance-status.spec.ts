import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { CurrentUserService, KycApiClient, AmlApiClient } from 'shared';
import { ComplianceStatus } from './compliance-status';

describe('ComplianceStatus', () => {
  let fixture: ComponentFixture<ComplianceStatus>;
  let kycChecksClient: { listByCustomer: ReturnType<typeof vi.fn> };
  let amlScreeningsClient: { listByCustomer: ReturnType<typeof vi.fn> };
  let currentUserService: { subjectId: ReturnType<typeof signal<string | null>> };

  const customerId = 'a1b2c3d4-0000-0000-0000-000000000000';

  const kycCheck: KycApiClient.KycCheckResponse = {
    id: 'kyc-1',
    customerId,
    status: KycApiClient.KycCheckResponse.StatusEnum.Approved,
    reason: 'Verified identity documents',
  };

  const screening: AmlApiClient.ScreeningResponse = {
    id: 'aml-1',
    customerId,
    status: AmlApiClient.ScreeningResponse.StatusEnum.Completed,
    outcome: AmlApiClient.ScreeningResponse.OutcomeEnum.Clear,
  };

  async function setup(subjectId: string | null = customerId) {
    kycChecksClient = { listByCustomer: vi.fn() };
    amlScreeningsClient = { listByCustomer: vi.fn() };
    currentUserService = { subjectId: signal(subjectId) };

    await TestBed.configureTestingModule({
      imports: [ComplianceStatus],
      providers: [
        { provide: CurrentUserService, useValue: currentUserService },
        { provide: KycApiClient.KYCChecksClient, useValue: kycChecksClient },
        { provide: AmlApiClient.AMLScreeningsClient, useValue: amlScreeningsClient },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ComplianceStatus);
  }

  it('shows KYC and AML status once both load', async () => {
    await setup();
    kycChecksClient.listByCustomer.mockReturnValue(
      of({ success: true, data: [kycCheck], meta: null }),
    );
    amlScreeningsClient.listByCustomer.mockReturnValue(
      of({ success: true, data: [screening], meta: null }),
    );
    fixture.detectChanges();

    expect(kycChecksClient.listByCustomer).toHaveBeenCalledWith(customerId);
    expect(amlScreeningsClient.listByCustomer).toHaveBeenCalledWith(customerId);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('APPROVED');
    expect(el.textContent).toContain('Verified identity documents');
    expect(el.textContent).toContain('COMPLETED');
    expect(el.textContent).toContain('CLEAR');
  });

  it('shows independent empty states for KYC and AML', async () => {
    await setup();
    kycChecksClient.listByCustomer.mockReturnValue(of({ success: true, data: [], meta: null }));
    amlScreeningsClient.listByCustomer.mockReturnValue(of({ success: true, data: [], meta: null }));
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No KYC review has been requested yet.');
    expect(el.textContent).toContain('No AML screening has been requested yet.');
  });

  it('shows an independent, retryable error state for KYC without affecting AML', async () => {
    await setup();
    kycChecksClient.listByCustomer.mockReturnValue(throwError(() => new Error('kyc down')));
    amlScreeningsClient.listByCustomer.mockReturnValue(
      of({ success: true, data: [screening], meta: null }),
    );
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('lib-error-state')).toBeTruthy();
    expect(el.textContent).toContain('CLEAR');

    kycChecksClient.listByCustomer.mockReturnValue(
      of({ success: true, data: [kycCheck], meta: null }),
    );
    (el.querySelector('lib-error-state button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(el.textContent).toContain('APPROVED');
  });
});
