import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import {
  CurrentUserService,
  ErrorState,
  InvestmentApiClient,
  LoadingSpinner,
  ReportingApiClient,
} from 'shared';
import { SubscriptionsStore } from './subscriptions.store';

/**
 * Cross-customer subscription management (Reporting Service's read-model).
 * `confirm-payment`/`cancel` share one role gate on Investment Service —
 * see subscriptions.store.ts's Javadoc. The "New Subscription" form shares
 * that same gate: `requestSubscription` is `WRITE_ROLES` on Investment
 * Service too, it just had no Admin Portal form until now.
 */
@Component({
  selector: 'app-subscriptions',
  imports: [
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    LoadingSpinner,
    ErrorState,
  ],
  providers: [SubscriptionsStore],
  templateUrl: './subscriptions.html',
  styleUrl: './subscriptions.scss',
})
export class Subscriptions implements OnInit {
  protected readonly store = inject(SubscriptionsStore);
  private readonly currentUserService = inject(CurrentUserService);

  protected readonly busyIds = signal<ReadonlySet<string>>(new Set());
  protected readonly creating = signal(false);
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

  protected createSubscription(
    customerId: string,
    ownerId: string,
    portfolioId: string,
    fundCode: string,
    quantity: string,
    form: HTMLFormElement,
  ): void {
    if (this.creating()) {
      return;
    }
    const parsedQuantity = Number(quantity);
    if (
      !customerId.trim() ||
      !ownerId.trim() ||
      !portfolioId.trim() ||
      !fundCode.trim() ||
      !Number.isFinite(parsedQuantity) ||
      parsedQuantity <= 0
    ) {
      return;
    }

    this.creating.set(true);
    const request: InvestmentApiClient.RequestSubscriptionRequest = {
      customerId: customerId.trim(),
      ownerId: ownerId.trim(),
      portfolioId: portfolioId.trim(),
      fundCode: fundCode.trim(),
      quantity: parsedQuantity,
    };
    this.store.create(request).subscribe({
      next: () => {
        this.creating.set(false);
        form.reset();
        this.store.load();
      },
      error: () => this.creating.set(false),
    });
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
