export * from './canary-admin.service';
import { CanaryAdminClient } from './canary-admin.service';
export * from './canary-admin.serviceInterface';
export * from './customer-lookup-canary.service';
import { CustomerLookupCanaryClient } from './customer-lookup-canary.service';
export * from './customer-lookup-canary.serviceInterface';
export const APIS = [CanaryAdminClient, CustomerLookupCanaryClient];
