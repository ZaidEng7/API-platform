import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { CurrentUserService, PortfolioApiClient } from 'shared';
import { MyPortfolio } from './my-portfolio';

describe('MyPortfolio', () => {
  let fixture: ComponentFixture<MyPortfolio>;
  let portfoliosClient: {
    listByOwner: ReturnType<typeof vi.fn>;
    valuate: ReturnType<typeof vi.fn>;
  };
  let currentUserService: { subjectId: ReturnType<typeof signal<string | null>> };

  const ownerId = 'a1b2c3d4-0000-0000-0000-000000000000';

  const portfolio: PortfolioApiClient.PortfolioResponse = {
    id: 'portfolio-1',
    customerId: ownerId,
    ownerId,
    name: 'Growth Portfolio',
    currency: 'USD',
    status: PortfolioApiClient.PortfolioResponse.StatusEnum.Active,
  };

  const valuation: PortfolioApiClient.PortfolioValuationResponse = {
    portfolioId: 'portfolio-1',
    currency: 'USD',
    totalValue: 1500,
    positions: [
      { fundCode: 'GLOBAL-EQUITY-01', quantity: 100, navPerShare: 15, marketValue: 1500 },
    ],
  };

  async function setup(subjectId: string | null = ownerId) {
    portfoliosClient = { listByOwner: vi.fn(), valuate: vi.fn() };
    currentUserService = { subjectId: signal(subjectId) };

    await TestBed.configureTestingModule({
      imports: [MyPortfolio],
      providers: [
        { provide: CurrentUserService, useValue: currentUserService },
        { provide: PortfolioApiClient.PortfoliosClient, useValue: portfoliosClient },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MyPortfolio);
  }

  it('shows a loading spinner then the list of portfolios', async () => {
    await setup();
    portfoliosClient.listByOwner.mockReturnValue(
      of({ success: true, data: [portfolio], meta: null }),
    );
    fixture.detectChanges();

    expect(portfoliosClient.listByOwner).toHaveBeenCalledWith(ownerId);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Growth Portfolio');
    expect(el.querySelector('lib-error-state')).toBeNull();
  });

  it('shows an empty state when the investor has no portfolios', async () => {
    await setup();
    portfoliosClient.listByOwner.mockReturnValue(of({ success: true, data: [], meta: null }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain("don't have any portfolios");
  });

  it('shows an error state and retries on demand', async () => {
    await setup();
    portfoliosClient.listByOwner.mockReturnValue(throwError(() => new Error('network error')));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('lib-error-state')).toBeTruthy();

    portfoliosClient.listByOwner.mockReturnValue(
      of({ success: true, data: [portfolio], meta: null }),
    );
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Growth Portfolio');
  });

  it('does not call the API until the current user id is resolved', async () => {
    await setup(null);
    portfoliosClient.listByOwner.mockReturnValue(of({ success: true, data: [], meta: null }));
    fixture.detectChanges();

    expect(portfoliosClient.listByOwner).not.toHaveBeenCalled();
  });

  it('loads and displays holdings when "View holdings" is clicked', async () => {
    await setup();
    portfoliosClient.listByOwner.mockReturnValue(
      of({ success: true, data: [portfolio], meta: null }),
    );
    portfoliosClient.valuate.mockReturnValue(of({ success: true, data: valuation, meta: null }));
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(portfoliosClient.valuate).toHaveBeenCalledWith('portfolio-1');
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('GLOBAL-EQUITY-01');
    expect(el.textContent).toContain('1500');
  });

  it('shows a retryable error state when holdings fail to load', async () => {
    await setup();
    portfoliosClient.listByOwner.mockReturnValue(
      of({ success: true, data: [portfolio], meta: null }),
    );
    portfoliosClient.valuate.mockReturnValue(throwError(() => new Error('valuation failed')));
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain("Couldn't load holdings");
  });
});
