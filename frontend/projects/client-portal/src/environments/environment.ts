/**
 * Production build defaults. Real deployments should replace these via
 * build-time environment file substitution (see angular.json's
 * fileReplacements) — same "safe default, override per environment"
 * convention the backend services use for their own config.
 */
export const environment = {
  production: true,
  apiBaseUrl: 'http://localhost:8080',
  keycloak: {
    issuer: 'http://localhost:8082/realms/company',
    clientId: 'gateway-portal',
  },
};
