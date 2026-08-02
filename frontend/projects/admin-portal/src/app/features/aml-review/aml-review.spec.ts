import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { AmlApiClient, CurrentUserService, ReportingApiClient } from 'shared';
import { AmlReview } from './aml-review';

describe('AmlReview', () => {
  let fixture: ComponentFixture<AmlReview>;
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

  async function setup(roles: string[] = []) {
    reportsClient = { listAmlScreenings: vi.fn() };
    amlScreeningsClient = { recordResult: vi.fn(), recordFailure: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [AmlReview],
      providers: [
        { provide: CurrentUserService, useValue: { roles: signal(roles) } },
        { provide: ReportingApiClient.ReportsClient, useValue: reportsClient },
        { provide: AmlApiClient.AMLScreeningsClient, useValue: amlScreeningsClient },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AmlReview);
  }

  it('lists AML screenings with their status', async () => {
    await setup();
    reportsClient.listAmlScreenings.mockReturnValue(
      of({ success: true, data: [screening({})], meta: null }),
    );
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('customer-1');
    expect(el.textContent).toContain('IN_PROGRESS');
  });

  it('shows an empty state when there are no screenings', async () => {
    await setup();
    reportsClient.listAmlScreenings.mockReturnValue(of({ success: true, data: [], meta: null }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No AML screenings found');
  });

  it('shows a retryable error state on failure', async () => {
    await setup();
    reportsClient.listAmlScreenings.mockReturnValue(throwError(() => new Error('network error')));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('lib-error-state')).toBeTruthy();
  });

  it('shows only the result form for a compliance-only role', async () => {
    await setup(['compliance']);
    reportsClient.listAmlScreenings.mockReturnValue(
      of({ success: true, data: [screening({})], meta: null }),
    );
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Clear');
    expect(el.textContent).toContain('Flag as Hit');
    expect(el.textContent).not.toContain('Mark Failed');
  });

  it('shows only the failure form for an operations-only role', async () => {
    await setup(['operations']);
    reportsClient.listAmlScreenings.mockReturnValue(
      of({ success: true, data: [screening({})], meta: null }),
    );
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).not.toContain('Clear');
    expect(el.textContent).toContain('Mark Failed');
  });

  it('lets compliance clear a screening with notes', async () => {
    await setup(['compliance']);
    reportsClient.listAmlScreenings.mockReturnValue(
      of({ success: true, data: [screening({})], meta: null }),
    );
    amlScreeningsClient.recordResult.mockReturnValue(of({ success: true }));
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const input = el.querySelector('.reason-input') as HTMLInputElement;
    input.value = 'Nothing found';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const clearButton = Array.from(el.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Clear',
    ) as HTMLButtonElement;
    clearButton.click();

    expect(amlScreeningsClient.recordResult).toHaveBeenCalledWith('screening-1', {
      outcome: 'CLEAR',
      notes: 'Nothing found',
    });
  });
});
