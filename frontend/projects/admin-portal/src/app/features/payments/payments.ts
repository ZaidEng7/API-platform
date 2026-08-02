import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { CurrentUserService, ErrorState, LoadingSpinner, ReportingApiClient } from 'shared';
import { PaymentsStore } from './payments.store';

/**
 * Cross-customer payment management (Reporting Service's read-model).
 * `settle`/`fail` are both `hasRole('OPERATIONS')` only on Payment
 * Service — see payments.store.ts's Javadoc. `settle` needs no body,
 * `fail` needs a reason — same mixed shape as Document's verify/reject,
 * just narrower to one role instead of compliance.
 */
@Component({
  selector: 'app-payments',
  imports: [DatePipe, FormsModule, MatButtonModule, MatChipsModule, LoadingSpinner, ErrorState],
  providers: [PaymentsStore],
  templateUrl: './payments.html',
  styleUrl: './payments.scss',
})
export class Payments implements OnInit {
  protected readonly store = inject(PaymentsStore);
  private readonly currentUserService = inject(CurrentUserService);

  protected readonly failureReasons: Record<string, string> = {};
  protected readonly busyIds = signal<ReadonlySet<string>>(new Set());
  protected readonly canManage = () => this.currentUserService.roles().includes('operations');

  ngOnInit(): void {
    this.store.load();
  }

  protected reload(): void {
    this.store.load();
  }

  protected statusClass(
    status: ReportingApiClient.PaymentTransferResponse.StatusEnum | undefined,
  ): string {
    switch (status) {
      case 'SETTLED':
        return 'status--settled';
      case 'FAILED':
        return 'status--failed';
      default:
        return 'status--pending';
    }
  }

  protected settle(transferId: string): void {
    if (this.busyIds().has(transferId)) {
      return;
    }
    this.setBusy(transferId, true);
    this.store.settle(transferId).subscribe({
      next: () => {
        this.setBusy(transferId, false);
        this.store.load();
      },
      error: () => this.setBusy(transferId, false),
    });
  }

  protected fail(transferId: string): void {
    const reason = this.failureReasons[transferId]?.trim();
    if (!reason || this.busyIds().has(transferId)) {
      return;
    }
    this.setBusy(transferId, true);
    this.store.fail(transferId, reason).subscribe({
      next: () => {
        this.setBusy(transferId, false);
        this.store.load();
      },
      error: () => this.setBusy(transferId, false),
    });
  }

  private setBusy(transferId: string, busy: boolean): void {
    this.busyIds.update((current) => {
      const next = new Set(current);
      if (busy) {
        next.add(transferId);
      } else {
        next.delete(transferId);
      }
      return next;
    });
  }
}
