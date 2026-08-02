import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CurrentUserService } from 'shared';
import { COMPLIANCE_REVIEW_ROLES, SUBSCRIPTIONS_AND_PAYMENTS_ROLES } from './roles';

/**
 * Sends `/` to the first section the signed-in user actually has a role
 * for, since Admin Portal (unlike Client Portal) has no single "home" every
 * staff role can see. Falls back to /access-denied for a role with no
 * section at all (e.g. `administrator` — see roles.ts's Javadoc).
 */
export const defaultRedirectGuard: CanActivateFn = () => {
  const currentUserService = inject(CurrentUserService);
  const router = inject(Router);
  const roles = currentUserService.roles();

  if (roles.some((role) => COMPLIANCE_REVIEW_ROLES.includes(role))) {
    return router.createUrlTree(['/kyc-review']);
  }
  if (roles.some((role) => SUBSCRIPTIONS_AND_PAYMENTS_ROLES.includes(role))) {
    return router.createUrlTree(['/subscriptions']);
  }
  return router.createUrlTree(['/access-denied']);
};
