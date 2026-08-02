import { Component, effect, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { CurrentUserService, ErrorState, LoadingSpinner, PortfolioApiClient } from 'shared';

/**
 * Investor's own portfolios (Portfolio Service, guide §8.3 "Portfolio
 * positions"). Ownership is enforced server-side by comparing the
 * `ownerId` query param to the JWT `sub` claim — {@link CurrentUserService}
 * supplies that id, since there's no `/me` endpoint (guide §12.2).
 */
@Component({
  selector: 'app-my-portfolio',
  imports: [MatCardModule, MatButtonModule, LoadingSpinner, ErrorState],
  templateUrl: './my-portfolio.html',
  styleUrl: './my-portfolio.scss',
})
export class MyPortfolio {
  private readonly currentUserService = inject(CurrentUserService);
  private readonly portfoliosClient = inject(PortfolioApiClient.PortfoliosClient);

  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  protected readonly portfolios = signal<PortfolioApiClient.PortfolioResponse[]>([]);

  protected readonly selectedPortfolioId = signal<string | null>(null);
  protected readonly valuationLoading = signal(false);
  protected readonly valuationError = signal(false);
  protected readonly valuation = signal<PortfolioApiClient.PortfolioValuationResponse | null>(null);

  constructor() {
    effect(() => {
      const ownerId = this.currentUserService.subjectId();
      if (ownerId) {
        this.loadPortfolios(ownerId);
      }
    });
  }

  protected reload(): void {
    const ownerId = this.currentUserService.subjectId();
    if (ownerId) {
      this.loadPortfolios(ownerId);
    }
  }

  protected viewHoldings(portfolioId: string | null | undefined): void {
    if (!portfolioId) {
      return;
    }
    this.selectedPortfolioId.set(portfolioId);
    this.valuationLoading.set(true);
    this.valuationError.set(false);
    this.portfoliosClient.valuate(portfolioId).subscribe({
      next: (response) => {
        this.valuation.set(response.data ?? null);
        this.valuationLoading.set(false);
      },
      error: () => {
        this.valuationError.set(true);
        this.valuationLoading.set(false);
      },
    });
  }

  private loadPortfolios(ownerId: string): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.portfoliosClient.listByOwner(ownerId).subscribe({
      next: (response) => {
        this.portfolios.set(response.data ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }
}
