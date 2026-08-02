import { computed } from '@angular/core';
import {
  patchState,
  signalStoreFeature,
  withComputed,
  withMethods,
  withState,
} from '@ngrx/signals';

export interface QueueState<T> {
  readonly items: T[];
  readonly loading: boolean;
  readonly loadError: boolean;
}

/**
 * Reusable NgRx Signals store feature for the cross-entity "review queue"
 * state each Admin Portal section (KYC, AML, Document, Subscriptions,
 * Payments) needs — a paged list read from Reporting Service's read-models
 * (see roadmap.md's Phase C decision: "NgRx store setup for cross-entity
 * state (review queues, dashboards)"). Each feature composes this with its
 * own `load` rxMethod calling its own API client — this only owns the
 * shape/transitions every queue shares, not any one domain's data fetch.
 */
export function withQueueEntities<T>() {
  return signalStoreFeature(
    withState<QueueState<T>>({ items: [], loading: false, loadError: false }),
    withComputed((store) => ({
      isEmpty: computed(() => !store.loading() && !store.loadError() && store.items().length === 0),
    })),
    withMethods((store) => ({
      setLoading(): void {
        patchState(store, { loading: true, loadError: false });
      },
      setItems(items: T[]): void {
        patchState(store, { items, loading: false, loadError: false });
      },
      setLoadError(): void {
        patchState(store, { items: [], loading: false, loadError: true });
      },
    })),
  );
}
