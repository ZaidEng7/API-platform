import { Component, effect, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatChipsModule } from '@angular/material/chips';
import { CurrentUserService, ErrorState, LoadingSpinner, KycApiClient, AmlApiClient } from 'shared';

/**
 * Investor's own KYC review + AML screening status (read-only — the
 * decision/result endpoints stay staff-only, guide §8.3). Ownership is
 * enforced server-side by comparing the `customerId` query param to the
 * JWT `sub` claim, the same pattern as Portfolio/Investment Service (see
 * {@link CurrentUserService}'s Javadoc) — this platform has no separate
 * Identity-to-Party linkage, so `sub` doubles as both `ownerId` and
 * `customerId` for the signed-in investor.
 */
@Component({
  selector: 'app-compliance-status',
  imports: [MatChipsModule, DatePipe, LoadingSpinner, ErrorState],
  templateUrl: './compliance-status.html',
  styleUrl: './compliance-status.scss',
})
export class ComplianceStatus {
  private readonly currentUserService = inject(CurrentUserService);
  private readonly kycChecksClient = inject(KycApiClient.KYCChecksClient);
  private readonly amlScreeningsClient = inject(AmlApiClient.AMLScreeningsClient);

  protected readonly kycLoading = signal(true);
  protected readonly kycError = signal(false);
  protected readonly kycChecks = signal<KycApiClient.KycCheckResponse[]>([]);

  protected readonly amlLoading = signal(true);
  protected readonly amlError = signal(false);
  protected readonly screenings = signal<AmlApiClient.ScreeningResponse[]>([]);

  constructor() {
    effect(() => {
      const customerId = this.currentUserService.subjectId();
      if (customerId) {
        this.loadKycChecks(customerId);
        this.loadScreenings(customerId);
      }
    });
  }

  protected reloadKyc(): void {
    const customerId = this.currentUserService.subjectId();
    if (customerId) {
      this.loadKycChecks(customerId);
    }
  }

  protected reloadAml(): void {
    const customerId = this.currentUserService.subjectId();
    if (customerId) {
      this.loadScreenings(customerId);
    }
  }

  private loadKycChecks(customerId: string): void {
    this.kycLoading.set(true);
    this.kycError.set(false);
    this.kycChecksClient.listByCustomer(customerId).subscribe({
      next: (response) => {
        this.kycChecks.set(response.data ?? []);
        this.kycLoading.set(false);
      },
      error: () => {
        this.kycError.set(true);
        this.kycLoading.set(false);
      },
    });
  }

  private loadScreenings(customerId: string): void {
    this.amlLoading.set(true);
    this.amlError.set(false);
    this.amlScreeningsClient.listByCustomer(customerId).subscribe({
      next: (response) => {
        this.screenings.set(response.data ?? []);
        this.amlLoading.set(false);
      },
      error: () => {
        this.amlError.set(true);
        this.amlLoading.set(false);
      },
    });
  }
}
