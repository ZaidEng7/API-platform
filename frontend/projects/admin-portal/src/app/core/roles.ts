/**
 * Mirrors each backend controller's own `READ_ROLES` constant exactly
 * (lowercase Keycloak role names, not the `ROLE_X` Spring authority form —
 * see {@link CurrentUserService.roles}'s Javadoc). Kept in one place so nav
 * visibility and route guards can never drift out of sync with each other.
 *
 * Notably absent from every group: `administrator`. None of
 * KycCheckController/ScreeningController/DocumentController/
 * SubscriptionController/TransferController/ReportingController actually
 * grants that role read access today — an `administrator`-only account
 * would see an empty nav here, which accurately reflects current backend
 * permissions rather than a frontend bug.
 */

/** KycCheckController/ScreeningController/DocumentController's `READ_ROLES` (minus `investor`, staff-only here). */
export const COMPLIANCE_REVIEW_ROLES: readonly string[] = [
  'operations',
  'compliance',
  'customer-service',
  'auditor',
];

/** SubscriptionController/TransferController/ReportingController's `READ_ROLES` (minus `investor`, staff-only here). */
export const SUBSCRIPTIONS_AND_PAYMENTS_ROLES: readonly string[] = [
  'operations',
  'portfolio-manager',
  'auditor',
  'compliance',
];
