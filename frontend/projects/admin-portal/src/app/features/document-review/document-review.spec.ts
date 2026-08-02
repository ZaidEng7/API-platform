import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { CurrentUserService, DocumentApiClient, ReportingApiClient } from 'shared';
import { DocumentReview } from './document-review';

describe('DocumentReview', () => {
  let fixture: ComponentFixture<DocumentReview>;
  let reportsClient: { listDocuments: ReturnType<typeof vi.fn> };
  let documentsClient: { verify: ReturnType<typeof vi.fn>; reject: ReturnType<typeof vi.fn> };

  function document(
    overrides: Partial<ReportingApiClient.DocumentReportResponse>,
  ): ReportingApiClient.DocumentReportResponse {
    return {
      documentId: 'document-1',
      customerId: 'customer-1',
      documentType: 'PASSPORT',
      status: ReportingApiClient.DocumentReportResponse.StatusEnum.Uploaded,
      uploadedAt: '2026-08-01T00:00:00Z',
      ...overrides,
    };
  }

  async function setup(roles: string[] = []) {
    reportsClient = { listDocuments: vi.fn() };
    documentsClient = { verify: vi.fn(), reject: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [DocumentReview],
      providers: [
        { provide: CurrentUserService, useValue: { roles: signal(roles) } },
        { provide: ReportingApiClient.ReportsClient, useValue: reportsClient },
        { provide: DocumentApiClient.DocumentsClient, useValue: documentsClient },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DocumentReview);
  }

  it('lists documents with their type and status', async () => {
    await setup();
    reportsClient.listDocuments.mockReturnValue(
      of({ success: true, data: [document({})], meta: null }),
    );
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('customer-1');
    expect(el.textContent).toContain('PASSPORT');
    expect(el.textContent).toContain('UPLOADED');
  });

  it('shows an empty state when there are no documents', async () => {
    await setup();
    reportsClient.listDocuments.mockReturnValue(of({ success: true, data: [], meta: null }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No documents found');
  });

  it('shows a retryable error state on failure', async () => {
    await setup();
    reportsClient.listDocuments.mockReturnValue(throwError(() => new Error('network error')));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('lib-error-state')).toBeTruthy();
  });

  it('does not show a decision form for a non-compliance role', async () => {
    await setup(['operations']);
    reportsClient.listDocuments.mockReturnValue(
      of({ success: true, data: [document({})], meta: null }),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.decision-form')).toBeNull();
  });

  it('lets compliance verify a document with notes', async () => {
    await setup(['compliance']);
    reportsClient.listDocuments.mockReturnValue(
      of({ success: true, data: [document({})], meta: null }),
    );
    documentsClient.verify.mockReturnValue(of({ success: true }));
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const input = el.querySelector('.reason-input') as HTMLInputElement;
    input.value = 'Looks legitimate';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const verifyButton = Array.from(el.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Verify',
    ) as HTMLButtonElement;
    verifyButton.click();

    expect(documentsClient.verify).toHaveBeenCalledWith('document-1', {
      notes: 'Looks legitimate',
    });
  });
});
