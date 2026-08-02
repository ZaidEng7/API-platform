import { inject } from '@angular/core';
import { signalStore, withMethods } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, Observable, catchError, pipe, switchMap, tap } from 'rxjs';
import { KycApiClient, ReportingApiClient } from 'shared';
import { withQueueEntities } from '../../core/state/queue-store-feature';

/**
 * Component-provided (not root) — state resets each time the KYC Review
 * page is navigated to, which is the right default for a review queue.
 * Reads from Reporting Service's read-model (cross-customer, no customerId
 * required); decisions go straight to KYC Service's own `/decision`
 * endpoint, `hasRole('COMPLIANCE')` only (enforced again client-side in the
 * component — see canDecide there — but the backend is the real gate).
 */
export const KycReviewStore = signalStore(
  withQueueEntities<ReportingApiClient.KycCheckReportResponse>(),
  withMethods((store, reportsClient = inject(ReportingApiClient.ReportsClient)) => ({
    load: rxMethod<void>(
      pipe(
        tap(() => store.setLoading()),
        switchMap(() =>
          reportsClient.listKycChecks(undefined, 0, 200).pipe(
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
  withMethods((_store, kycChecksClient = inject(KycApiClient.KYCChecksClient)) => ({
    decide(
      id: string,
      outcome: KycApiClient.KycDecisionRequest.OutcomeEnum,
      reason: string,
    ): Observable<unknown> {
      return kycChecksClient.decide(id, { outcome, reason });
    },
  })),
);
