import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { CurrentUserService, ErrorState, LoadingSpinner, ReportingApiClient } from 'shared';
import { SubscriptionsStore } from './subscriptions.store';

/**
 * Cross-customer subscription management (Reporting Service's read-model).
 * `confirm-payment`/`cancel` share one role gate on Investment Service —
 * see subscriptions.store.ts's Javadoc. Neither action needs a reason,
 * so this is the simplest of the four queue features.
 */
@Component({
  selector: 'app-subscriptions',
  imports: [MatButtonModule, MatChipsModule, LoadingSpinner, ErrorState],
  providers: [SubscriptionsStore],
  templateUrl: './subscriptions.html',
  styleUrl: './subscriptions.scss',
})
export class Subscriptions implements OnInit {
  protected readonly store = inject(SubscriptionsStore);
  private readonly currentUserService = inject(CurrentUserService);

  protected readonly busyIds = signal<ReadonlySet<string>>(new Set());
  protected readonly canManage = () => {
    const roles = this.currentUserService.roles();
    return roles.includes('operations') || roles.includes('portfolio-manager');
  };

  ngOnInit(): void {
    this.store.load();
  }

  protected reload(): void {
    this.store.load();
  }

  protected statusClass(
    status: ReportingApiClient.SubscriptionReportResponse.StatusEnum | undefined,
  ): string {
    switch (status) {
      case 'CONFIRMED':
        return 'status--confirmed';
      case 'FAILED':
      case 'TIMED_OUT':
        return 'status--failed';
      case 'CANCELLED':
        return 'status--cancelled';
      default:
        return 'status--pending';
    }
  }

  protected confirmPayment(subscriptionId: string): void {
    if (this.busyIds().has(subscriptionId)) {
      return;
    }
    this.setBusy(subscriptionId, true);
    this.store.confirmPayment(subscriptionId).subscribe({
      next: () => {
        this.setBusy(subscriptionId, false);
        this.store.load();
      },
      error: () => this.setBusy(subscriptionId, false),
    });
  }

  protected cancel(subscriptionId: string): void {
    if (this.busyIds().has(subscriptionId)) {
      return;
    }
    this.setBusy(subscriptionId, true);
    this.store.cancel(subscriptionId).subscribe({
      next: () => {
        this.setBusy(subscriptionId, false);
        this.store.load();
      },
      error: () => this.setBusy(subscriptionId, false),
    });
  }

  private setBusy(subscriptionId: string, busy: boolean): void {
    this.busyIds.update((current) => {
      const next = new Set(current);
      if (busy) {
        next.add(subscriptionId);
      } else {
        next.delete(subscriptionId);
      }
      return next;
    });
  }
}
