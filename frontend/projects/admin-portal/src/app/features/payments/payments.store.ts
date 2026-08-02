import { inject } from '@angular/core';
import { signalStore, withMethods } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, Observable, catchError, pipe, switchMap, tap } from 'rxjs';
import { PaymentApiClient, ReportingApiClient } from 'shared';
import { withQueueEntities } from '../../core/state/queue-store-feature';

/**
 * Component-provided — see kyc-review.store.ts's Javadoc for the same
 * reasoning. Reads from Reporting Service's read-model (the one domain
 * that already had a cross-customer list before Phase C — see roadmap.md's
 * Phase C decision); both `settle` and `fail` on Payment Service's own
 * `TransferController` are `hasRole('OPERATIONS')` only — the narrowest
 * role gate of all five queue features.
 */
export const PaymentsStore = signalStore(
  withQueueEntities<ReportingApiClient.PaymentTransferResponse>(),
  withMethods((store, reportsClient = inject(ReportingApiClient.ReportsClient)) => ({
    load: rxMethod<void>(
      pipe(
        tap(() => store.setLoading()),
        switchMap(() =>
          reportsClient.listPayments(undefined, 0, 200).pipe(
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
  withMethods((_store, paymentsClient = inject(PaymentApiClient.PaymentsClient)) => ({
    settle(id: string): Observable<unknown> {
      return paymentsClient.settle(id);
    },
    fail(id: string, reason: string): Observable<unknown> {
      return paymentsClient.fail(id, { reason });
    },
  })),
);
