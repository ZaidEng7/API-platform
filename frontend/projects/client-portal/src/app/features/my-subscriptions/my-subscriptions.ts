import { Component, effect, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatChipsModule } from '@angular/material/chips';
import { CurrentUserService, ErrorState, LoadingSpinner, InvestmentApiClient } from 'shared';

/**
 * Investor's own fund subscriptions (Investment Service, guide §8.4's
 * subscription saga). Ownership enforced server-side the same way as
 * Portfolio Service — see {@link CurrentUserService}'s Javadoc.
 */
@Component({
  selector: 'app-my-subscriptions',
  imports: [MatChipsModule, DatePipe, LoadingSpinner, ErrorState],
  templateUrl: './my-subscriptions.html',
  styleUrl: './my-subscriptions.scss',
})
export class MySubscriptions {
  private readonly currentUserService = inject(CurrentUserService);
  private readonly subscriptionsClient = inject(InvestmentApiClient.SubscriptionsClient);

  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  protected readonly subscriptions = signal<InvestmentApiClient.SubscriptionResponse[]>([]);

  constructor() {
    effect(() => {
      const ownerId = this.currentUserService.subjectId();
      if (ownerId) {
        this.loadSubscriptions(ownerId);
      }
    });
  }

  protected reload(): void {
    const ownerId = this.currentUserService.subjectId();
    if (ownerId) {
      this.loadSubscriptions(ownerId);
    }
  }

  protected statusClass(
    status: InvestmentApiClient.SubscriptionResponse.StatusEnum | undefined,
  ): string {
    switch (status) {
      case InvestmentApiClient.SubscriptionResponse.StatusEnum.Confirmed:
        return 'status--confirmed';
      case InvestmentApiClient.SubscriptionResponse.StatusEnum.Failed:
      case InvestmentApiClient.SubscriptionResponse.StatusEnum.TimedOut:
        return 'status--failed';
      case InvestmentApiClient.SubscriptionResponse.StatusEnum.Cancelled:
        return 'status--cancelled';
      default:
        return 'status--pending';
    }
  }

  private loadSubscriptions(ownerId: string): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.subscriptionsClient.listByOwner(ownerId).subscribe({
      next: (response) => {
        this.subscriptions.set(response.data ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }
}
