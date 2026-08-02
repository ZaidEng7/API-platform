import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { DocumentApiClient, ReportingApiClient } from 'shared';
import { DocumentReviewStore } from './document-review.store';

describe('DocumentReviewStore', () => {
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

  function setup() {
    reportsClient = { listDocuments: vi.fn() };
    documentsClient = { verify: vi.fn(), reject: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        DocumentReviewStore,
        { provide: ReportingApiClient.ReportsClient, useValue: reportsClient },
        { provide: DocumentApiClient.DocumentsClient, useValue: documentsClient },
      ],
    });
    return TestBed.inject(DocumentReviewStore);
  }

  it('loads documents from Reporting Service into the queue', () => {
    const store = setup();
    reportsClient.listDocuments.mockReturnValue(
      of({ success: true, data: [document({})], meta: null }),
    );

    store.load();

    expect(reportsClient.listDocuments).toHaveBeenCalledWith(undefined, 0, 200);
    expect(store.items()).toEqual([document({})]);
  });

  it('sets loadError when the read fails', () => {
    const store = setup();
    reportsClient.listDocuments.mockReturnValue(throwError(() => new Error('network error')));

    store.load();

    expect(store.loadError()).toBe(true);
  });

  it('verify delegates to DocumentsClient with notes', () => {
    const store = setup();
    documentsClient.verify.mockReturnValue(of({ success: true }));

    store.verify('document-1', 'Looks legitimate').subscribe();

    expect(documentsClient.verify).toHaveBeenCalledWith('document-1', {
      notes: 'Looks legitimate',
    });
  });

  it('reject delegates to DocumentsClient with notes', () => {
    const store = setup();
    documentsClient.reject.mockReturnValue(of({ success: true }));

    store.reject('document-1', 'Blurry image').subscribe();

    expect(documentsClient.reject).toHaveBeenCalledWith('document-1', { notes: 'Blurry image' });
  });
});
