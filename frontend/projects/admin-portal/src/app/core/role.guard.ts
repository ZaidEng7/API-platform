import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CurrentUserService } from 'shared';

/**
 * Blocks a route unless the signed-in staff user holds at least one of
 * `allowedRoles` — Admin Portal is the one app in this platform where
 * different sections are gated to different staff roles (unlike Client
 * Portal, which gates its whole UI behind isAuthenticated() once — see
 * app.routes.ts's own comment there).
 */
export function roleGuard(allowedRoles: readonly string[]): CanActivateFn {
  return () => {
    const currentUserService = inject(CurrentUserService);
    const router = inject(Router);

    const hasAccess = currentUserService.roles().some((role) => allowedRoles.includes(role));
    return hasAccess ? true : router.createUrlTree(['/access-denied']);
  };
}
