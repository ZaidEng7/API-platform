import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ErrorState, GatewayApiClient, LoadingSpinner } from 'shared';

/**
 * The Gateway's canary proxy has no single fixed response schema — `data`'s
 * shape depends on which backend answered (Customer Service or crm-adapter),
 * which is exactly the point of the strangler-fig demo (see
 * CustomerLookupCanaryController's own Javadoc). openapi-generator can only
 * type the raw body as `string`; Angular's `responseType: 'json'` still
 * auto-parses it into this envelope shape at runtime — the generated
 * `string` type is a codegen artifact, not what actually comes back.
 */
interface CanaryLookupEnvelope {
  success: boolean;
  data: Record<string, unknown> | null;
}

/**
 * Manual search-by-ID, not an auto-loaded "my profile" page: Customer
 * Service (the real backend behind this lookup) generates its own id on
 * creation and has no link to the signed-in investor's Keycloak `sub`,
 * unlike Portfolio/Investment/KYC/AML Service's caller-supplied
 * ownerId/customerId. The id entered here may resolve against either
 * backend depending on the live canary weight — this component doesn't
 * control or know that ahead of the call.
 */
@Component({
  selector: 'app-party-lookup',
  imports: [
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    LoadingSpinner,
    ErrorState,
  ],
  templateUrl: './party-lookup.html',
  styleUrl: './party-lookup.scss',
})
export class PartyLookup {
  private readonly lookupClient = inject(GatewayApiClient.CustomerLookupCanaryClient);

  protected readonly loading = signal(false);
  protected readonly searched = signal(false);
  protected readonly notFound = signal(false);
  protected readonly error = signal(false);
  protected readonly source = signal<string | null>(null);
  protected readonly result = signal<Record<string, unknown> | null>(null);

  protected search(id: string): void {
    const trimmed = id.trim();
    if (!trimmed) {
      return;
    }

    this.loading.set(true);
    this.searched.set(true);
    this.notFound.set(false);
    this.error.set(false);
    this.result.set(null);
    this.source.set(null);

    this.lookupClient.lookup(trimmed, 'response').subscribe({
      next: (response) => {
        this.source.set(response.headers.get('X-Canary-Target'));
        const envelope = response.body as unknown as CanaryLookupEnvelope | null;
        this.result.set(envelope?.data ?? null);
        this.loading.set(false);
      },
      error: (httpError: { status?: number }) => {
        this.loading.set(false);
        if (httpError?.status === 404) {
          this.notFound.set(true);
        } else {
          this.error.set(true);
        }
      },
    });
  }
}
