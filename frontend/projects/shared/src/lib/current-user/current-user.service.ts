import { Injectable, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { map } from 'rxjs';

/**
 * Resolves the current user's own id for ownership-scoped backend reads.
 * Portfolio/Investment/KYC/AML Service all compare a caller-supplied
 * `ownerId`/`customerId` query param to the JWT `sub` claim server-side —
 * there is no `/me` endpoint anywhere in this platform, so the frontend
 * must read `sub` from its own access token and supply it explicitly
 * (guide §12.2; the comparison against `sub` *is* the ownership check).
 */
@Injectable({ providedIn: 'root' })
export class CurrentUserService {
  private readonly oidcSecurityService = inject(OidcSecurityService);

  readonly subjectId = toSignal(
    this.oidcSecurityService
      .getPayloadFromAccessToken()
      .pipe(map((payload: { sub?: string }) => payload?.sub ?? null)),
    { initialValue: null },
  );
}
