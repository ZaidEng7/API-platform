import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { signal } from '@angular/core';
import { CurrentUserService } from 'shared';
import { defaultRedirectGuard } from './default-redirect.guard';

describe('defaultRedirectGuard', () => {
  function setup(roles: string[]) {
    TestBed.configureTestingModule({
      providers: [{ provide: CurrentUserService, useValue: { roles: signal(roles) } }],
    });
    return { router: TestBed.inject(Router) };
  }

  it('redirects a compliance-review role to /kyc-review', () => {
    const { router } = setup(['compliance']);

    const result = TestBed.runInInjectionContext(() =>
      defaultRedirectGuard({} as never, {} as never),
    );

    expect(result).toEqual(router.createUrlTree(['/kyc-review']));
  });

  it('redirects a subscriptions/payments-only role to /subscriptions', () => {
    const { router } = setup(['portfolio-manager']);

    const result = TestBed.runInInjectionContext(() =>
      defaultRedirectGuard({} as never, {} as never),
    );

    expect(result).toEqual(router.createUrlTree(['/subscriptions']));
  });

  it('redirects a role with no accessible section to /access-denied', () => {
    const { router } = setup(['administrator']);

    const result = TestBed.runInInjectionContext(() =>
      defaultRedirectGuard({} as never, {} as never),
    );

    expect(result).toEqual(router.createUrlTree(['/access-denied']));
  });
});
