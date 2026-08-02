import { inject } from '@angular/core';
import { signalStore, withMethods } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, Observable, catchError, pipe, switchMap, tap } from 'rxjs';
import { AmlApiClient, ReportingApiClient } from 'shared';
import { withQueueEntities } from '../../core/state/queue-store-feature';

/**
 * Component-provided — see kyc-review.store.ts's Javadoc for the same
 * reasoning. Reads from Reporting Service's read-model; two distinct
 * decision actions go to AML Service's own endpoints, each with a
 * different role gate: `/result` is `hasRole('COMPLIANCE')` only,
 * `/fail` is `hasRole('OPERATIONS')` only — unlike KYC's single
 * `/decision` endpoint gated to one role.
 */
export const AmlReviewStore = signalStore(
  withQueueEntities<ReportingApiClient.AmlScreeningReportResponse>(),
  withMethods((store, reportsClient = inject(ReportingApiClient.ReportsClient)) => ({
    load: rxMethod<void>(
      pipe(
        tap(() => store.setLoading()),
        switchMap(() =>
          reportsClient.listAmlScreenings(undefined, 0, 200).pipe(
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
  withMethods((_store, amlScreeningsClient = inject(AmlApiClient.AMLScreeningsClient)) => ({
    recordResult(
      id: string,
      outcome: AmlApiClient.ScreeningResultRequest.OutcomeEnum,
      notes: string,
    ): Observable<unknown> {
      return amlScreeningsClient.recordResult(id, { outcome, notes });
    },
    recordFailure(id: string, reason: string): Observable<unknown> {
      return amlScreeningsClient.recordFailure(id, { reason });
    },
  })),
);
