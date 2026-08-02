import { inject } from '@angular/core';
import { signalStore, withMethods } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, Observable, catchError, pipe, switchMap, tap } from 'rxjs';
import { DocumentApiClient, ReportingApiClient } from 'shared';
import { withQueueEntities } from '../../core/state/queue-store-feature';

/**
 * Component-provided — see kyc-review.store.ts's Javadoc for the same
 * reasoning. Reads from Reporting Service's read-model; both `/verify`
 * and `/reject` on Document Service are `hasRole('COMPLIANCE')` only.
 */
export const DocumentReviewStore = signalStore(
  withQueueEntities<ReportingApiClient.DocumentReportResponse>(),
  withMethods((store, reportsClient = inject(ReportingApiClient.ReportsClient)) => ({
    load: rxMethod<void>(
      pipe(
        tap(() => store.setLoading()),
        switchMap(() =>
          reportsClient.listDocuments(undefined, 0, 200).pipe(
            tap((response) => store.setItems(response.data ?? [])),
            catchError(() => {
              store.setLoadError();
              return EMPTY;
            }),
          ),
        ),
      ),
    ),
  })),
  withMethods((_store, documentsClient = inject(DocumentApiClient.DocumentsClient)) => ({
    verify(id: string, notes: string): Observable<unknown> {
      return documentsClient.verify(id, { notes });
    },
    reject(id: string, notes: string): Observable<unknown> {
      return documentsClient.reject(id, { notes });
    },
  })),
);
