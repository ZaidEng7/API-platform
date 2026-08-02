import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { signal } from '@angular/core';
import { CurrentUserService } from 'shared';
import { roleGuard } from './role.guard';

describe('roleGuard', () => {
  function setup(roles: string[]) {
    TestBed.configureTestingModule({
      providers: [{ provide: CurrentUserService, useValue: { roles: signal(roles) } }],
    });
    const router = TestBed.inject(Router);
    return { router };
  }

  it('allows activation when the user holds one of the allowed roles', () => {
    setup(['compliance']);
    const guard = roleGuard(['operations', 'compliance']);

    const result = TestBed.runInInjectionContext(() => guard({} as never, {} as never));

    expect(result).toBe(true);
  });

  it('redirects to /access-denied when the user holds none of the allowed roles', () => {
    const { router } = setup(['auditor']);
    const guard = roleGuard(['operations', 'portfolio-manager']);

    const result = TestBed.runInInjectionContext(() => guard({} as never, {} as never));

    expect(result).toEqual(router.createUrlTree(['/access-denied']));
  });
});
