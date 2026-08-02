import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import {
  CurrentUserService,
  ErrorState,
  KycApiClient,
  LoadingSpinner,
  ReportingApiClient,
} from 'shared';
import { KycReviewStore } from './kyc-review.store';

/**
 * Cross-customer KYC review queue (Reporting Service's read-model, guide
 * §8.3) with an inline approve/reject decision form. Only a `compliance`
 * role can actually decide (KycCheckController's `/decision` is
 * `hasRole('COMPLIANCE')` only) — every other staff role that can reach
 * this page (operations, customer-service, auditor, per roleGuard) sees the
 * queue read-only.
 */
@Component({
  selector: 'app-kyc-review',
  imports: [DatePipe, FormsModule, MatButtonModule, MatChipsModule, LoadingSpinner, ErrorState],
  providers: [KycReviewStore],
  templateUrl: './kyc-review.html',
  styleUrl: './kyc-review.scss',
})
export class KycReview implements OnInit {
  protected readonly store = inject(KycReviewStore);
  private readonly currentUserService = inject(CurrentUserService);

  protected readonly reasons: Record<string, string> = {};
  protected readonly busyIds = signal<ReadonlySet<string>>(new Set());
  protected readonly canDecide = () => this.currentUserService.roles().includes('compliance');

  ngOnInit(): void {
    this.store.load();
  }

  protected reload(): void {
    this.store.load();
  }

  protected statusClass(
    status: ReportingApiClient.KycCheckReportResponse.StatusEnum | undefined,
  ): string {
    switch (status) {
      case 'APPROVED':
        return 'status--approved';
      case 'REJECTED':
        return 'status--rejected';
      default:
        return 'status--pending';
    }
  }

  protected decide(checkId: string, outcome: KycApiClient.KycDecisionRequest.OutcomeEnum): void {
    const reason = this.reasons[checkId]?.trim();
    if (!reason || this.busyIds().has(checkId)) {
      return;
    }

    this.setBusy(checkId, true);
    this.store.decide(checkId, outcome, reason).subscribe({
      next: () => {
        this.setBusy(checkId, false);
        this.store.load();
      },
      error: () => this.setBusy(checkId, false),
    });
  }

  private setBusy(checkId: string, busy: boolean): void {
    this.busyIds.update((current) => {
      const next = new Set(current);
      if (busy) {
        next.add(checkId);
      } else {
        next.delete(checkId);
      }
      return next;
    });
  }
}
