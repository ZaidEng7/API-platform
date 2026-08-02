import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { CurrentUserService, ErrorState, LoadingSpinner, ReportingApiClient } from 'shared';
import { AmlReviewStore } from './aml-review.store';

/**
 * Cross-customer AML review queue (Reporting Service's read-model). Two
 * independent decision forms per row, since AML Service gates `/result`
 * (compliance sign-off: CLEAR/HIT) and `/fail` (operations: technical
 * failure) to different roles — see aml-review.store.ts's Javadoc.
 */
@Component({
  selector: 'app-aml-review',
  imports: [DatePipe, FormsModule, MatButtonModule, MatChipsModule, LoadingSpinner, ErrorState],
  providers: [AmlReviewStore],
  templateUrl: './aml-review.html',
  styleUrl: './aml-review.scss',
})
export class AmlReview implements OnInit {
  protected readonly store = inject(AmlReviewStore);
  private readonly currentUserService = inject(CurrentUserService);

  protected readonly notes: Record<string, string> = {};
  protected readonly failureReasons: Record<string, string> = {};
  protected readonly busyIds = signal<ReadonlySet<string>>(new Set());
  protected readonly canRecordResult = () => this.currentUserService.roles().includes('compliance');
  protected readonly canRecordFailure = () =>
    this.currentUserService.roles().includes('operations');

  ngOnInit(): void {
    this.store.load();
  }

  protected reload(): void {
    this.store.load();
  }

  protected statusClass(
    status: ReportingApiClient.AmlScreeningReportResponse.StatusEnum | undefined,
  ): string {
    switch (status) {
      case 'COMPLETED':
        return 'status--completed';
      case 'FAILED':
        return 'status--failed';
      default:
        return 'status--in-progress';
    }
  }

  protected recordResult(
    screeningId: string,
    outcome: ReportingApiClient.AmlScreeningReportResponse.OutcomeEnum,
  ): void {
    const notes = this.notes[screeningId]?.trim();
    if (!notes || this.busyIds().has(screeningId)) {
      return;
    }

    this.setBusy(screeningId, true);
    this.store.recordResult(screeningId, outcome, notes).subscribe({
      next: () => {
        this.setBusy(screeningId, false);
        this.store.load();
      },
      error: () => this.setBusy(screeningId, false),
    });
  }

  protected recordFailure(screeningId: string): void {
    const reason = this.failureReasons[screeningId]?.trim();
    if (!reason || this.busyIds().has(screeningId)) {
      return;
    }

    this.setBusy(screeningId, true);
    this.store.recordFailure(screeningId, reason).subscribe({
      next: () => {
        this.setBusy(screeningId, false);
        this.store.load();
      },
      error: () => this.setBusy(screeningId, false),
    });
  }

  private setBusy(screeningId: string, busy: boolean): void {
    this.busyIds.update((current) => {
      const next = new Set(current);
      if (busy) {
        next.add(screeningId);
      } else {
        next.delete(screeningId);
      }
      return next;
    });
  }
}
