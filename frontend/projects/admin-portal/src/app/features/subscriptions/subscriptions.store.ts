import { inject } from '@angular/core';
import { signalStore, withMethods } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, Observable, catchError, pipe, switchMap, tap } from 'rxjs';
import { InvestmentApiClient, ReportingApiClient } from 'shared';
import { withQueueEntities } from '../../core/state/queue-store-feature';

/**
 * Component-provided — see kyc-review.store.ts's Javadoc for the same
 * reasoning. Reads from Reporting Service's read-model; both
 * `confirm-payment` and `cancel` on Investment Service's own
 * `SubscriptionController` share one role gate:
 * `hasAnyRole('OPERATIONS', 'PORTFOLIO_MANAGER')`. Neither takes a
 * request body — no reason/notes form needed here, unlike KYC/AML/Document.
 */
export const SubscriptionsStore = signalStore(
  withQueueEntities<ReportingApiClient.SubscriptionReportResponse>(),
  withMethods((store, reportsClient = inject(ReportingApiClient.ReportsClient)) => ({
    load: rxMethod<void>(
      pipe(
        tap(() => store.setLoading()),
        switchMap(() =>
          reportsClient.listSubscriptions(undefined, 0, 200).pipe(
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
  withMethods((_store, subscriptionsClient = inject(InvestmentApiClient.SubscriptionsClient)) => ({
    confirmPayment(id: string): Observable<unknown> {
      return subscriptionsClient.confirmPayment(id);
    },
    cancel(id: string): Observable<unknown> {
      return subscriptionsClient.cancel(id);
    },
  })),
);
