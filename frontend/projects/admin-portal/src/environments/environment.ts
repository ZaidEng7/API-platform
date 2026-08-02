/**
 * Production build defaults. Real deployments should replace these via
 * build-time environment file substitution (see angular.json's
 * fileReplacements) — same "safe default, override per environment"
 * convention the backend services use for their own config.
 *
 * NOTE: the Keycloak `gateway-portal` client is currently only registered
 * for the http://localhost:4200 origin (Client Portal's dev port) — see
 * platform/identity/realm-export.json. Wiring real auth into Admin Portal
 * (Phase C) needs that client's redirectUris/webOrigins extended for this
 * app's own origin (dev: http://localhost:4201) first.
 */
export const environment = {
  production: true,
  apiBaseUrl: 'http://localhost:8080',
  keycloak: {
    issuer: 'http://localhost:8082/realms/company',
    clientId: 'gateway-portal',
  },
};
