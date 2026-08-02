import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { GatewayApiClient } from 'shared';
import { PartyLookup } from './party-lookup';

describe('PartyLookup', () => {
  let fixture: ComponentFixture<PartyLookup>;
  let lookupClient: { lookup: ReturnType<typeof vi.fn> };

  async function setup() {
    lookupClient = { lookup: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [PartyLookup],
      providers: [{ provide: GatewayApiClient.CustomerLookupCanaryClient, useValue: lookupClient }],
    }).compileComponents();

    fixture = TestBed.createComponent(PartyLookup);
    fixture.detectChanges();
  }

  function typeAndSubmit(id: string): void {
    const el: HTMLElement = fixture.nativeElement;
    const input = el.querySelector('input') as HTMLInputElement;
    input.value = id;
    input.dispatchEvent(new Event('input'));
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
  }

  it('looks up an id and shows the Customer Service shape with its source badge', async () => {
    await setup();
    lookupClient.lookup.mockReturnValue(
      of(
        new HttpResponse({
          body: {
            success: true,
            data: {
              id: '7e2edace-e8ce-4432-8d22-e2084216cb8f',
              fullName: 'Jane Investor',
              email: 'jane.investor@example.com',
              phone: '+15551234567',
              dateOfBirth: '1990-01-01',
              partyType: 'INDIVIDUAL',
            },
            meta: null,
          },
          headers: new HttpHeaders({ 'X-Canary-Target': 'customer-service' }),
        }),
      ),
    );

    typeAndSubmit('7e2edace-e8ce-4432-8d22-e2084216cb8f');
    fixture.detectChanges();

    expect(lookupClient.lookup).toHaveBeenCalledWith(
      '7e2edace-e8ce-4432-8d22-e2084216cb8f',
      'response',
    );
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Customer Service');
    expect(el.textContent).toContain('Jane Investor');
    expect(el.textContent).toContain('+15551234567');
    expect(el.textContent).toContain('INDIVIDUAL');
  });

  it('looks up an id and shows the legacy CRM shape with its source badge', async () => {
    await setup();
    lookupClient.lookup.mockReturnValue(
      of(
        new HttpResponse({
          body: {
            success: true,
            data: {
              id: 'cust-001',
              fullName: 'Legacy Jane Doe',
              email: 'legacy.jane@example.com',
              status: 'ACTIVE',
              vip: true,
            },
            meta: null,
          },
          headers: new HttpHeaders({ 'X-Canary-Target': 'crm-adapter' }),
        }),
      ),
    );

    typeAndSubmit('cust-001');
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Legacy CRM');
    expect(el.textContent).toContain('Legacy Jane Doe');
    expect(el.textContent).toContain('ACTIVE');
    expect(el.textContent).toContain('Yes');
  });

  it('shows a not-found message on a 404', async () => {
    await setup();
    lookupClient.lookup.mockReturnValue(throwError(() => ({ status: 404 })));

    typeAndSubmit('does-not-exist');
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No customer/party record found');
  });

  it('shows a retryable error state on other failures', async () => {
    await setup();
    lookupClient.lookup.mockReturnValue(throwError(() => ({ status: 503 })));

    typeAndSubmit('cust-001');
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('lib-error-state')).toBeTruthy();

    lookupClient.lookup.mockReturnValue(
      of(
        new HttpResponse({
          body: {
            success: true,
            data: { id: 'cust-001', fullName: 'Legacy Jane Doe' },
            meta: null,
          },
          headers: new HttpHeaders({ 'X-Canary-Target': 'crm-adapter' }),
        }),
      ),
    );
    (el.querySelector('lib-error-state button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(el.textContent).toContain('Legacy Jane Doe');
  });

  it('does not call the lookup client for a blank id', async () => {
    await setup();

    typeAndSubmit('   ');
    fixture.detectChanges();

    expect(lookupClient.lookup).not.toHaveBeenCalled();
  });
});
