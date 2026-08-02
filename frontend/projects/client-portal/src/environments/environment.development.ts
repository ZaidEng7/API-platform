export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080',
  keycloak: {
    issuer: 'http://localhost:8082/realms/company',
    clientId: 'gateway-portal',
  },
};
