import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import {
  CurrentUserService,
  ErrorState,
  LoadingSpinner,
  PaymentApiClient,
  ReportingApiClient,
} from 'shared';
import { PaymentsStore } from './payments.store';

/**
 * Cross-customer payment management (Reporting Service's read-model).
 * `settle`/`fail` are both `hasRole('OPERATIONS')` only on Payment
 * Service — see payments.store.ts's Javadoc. `settle` needs no body,
 * `fail` needs a reason — same mixed shape as Document's verify/reject,
 * just narrower to one role instead of compliance. The "New Payment" form
 * uses the wider `WRITE_ROLES` gate instead (`canCreate`), matching
 * `requestTransfer` itself — it's the only real way to get a Payment
 * Service record to exist at all, since nothing on the Subscriptions
 * screen (confirm-payment/cancel) ever creates one automatically.
 */
@Component({
  selector: 'app-payments',
  imports: [
    DatePipe,
    FormsModule,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    LoadingSpinner,
    ErrorState,
  ],
  providers: [PaymentsStore],
  templateUrl: './payments.html',
  styleUrl: './payments.scss',
})
export class Payments implements OnInit {
  protected readonly store = inject(PaymentsStore);
  private readonly currentUserService = inject(CurrentUserService);

  protected readonly failureReasons: Record<string, string> = {};
  protected readonly busyIds = signal<ReadonlySet<string>>(new Set());
  protected readonly creating = signal(false);
  protected readonly canManage = () => this.currentUserService.roles().includes('operations');
  protected readonly canCreate = () => {
    const roles = this.currentUserService.roles();
    return roles.includes('operations') || roles.includes('portfolio-manager');
  };

  ngOnInit(): void {
    this.store.load();
  }

  protected createPayment(
    customerId: string,
    ownerId: string,
    amount: string,
    currency: string,
    paymentMethodToken: string,
    reference: string,
    form: HTMLFormElement,
  ): void {
    if (this.creating()) {
      return;
    }
    const parsedAmount = Number(amount);
    if (
      !customerId.trim() ||
      !ownerId.trim() ||
      !currency.trim() ||
      !paymentMethodToken.trim() ||
      !Number.isFinite(parsedAmount) ||
      parsedAmount <= 0
    ) {
      return;
    }

    this.creating.set(true);
    const request: PaymentApiClient.RequestTransferRequest = {
      customerId: customerId.trim(),
      ownerId: ownerId.trim(),
      amount: parsedAmount,
      currency: currency.trim(),
      paymentMethodToken: paymentMethodToken.trim(),
      ...(reference.trim() ? { reference: reference.trim() } : {}),
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
