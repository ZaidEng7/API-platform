import { LogLevel } from 'angular-auth-oidc-client';
import * as oidcClient from 'angular-auth-oidc-client';
import { provideKeycloakAuth } from './auth.config';

vi.mock('angular-auth-oidc-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('angular-auth-oidc-client')>();
  return {
    ...actual,
    provideAuth: vi.fn(() => ({ ɵproviders: [] }) as never),
  };
});

describe('provideKeycloakAuth', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('configures Auth Code + PKCE against the given Keycloak realm/client', () => {
    provideKeycloakAuth({
      issuer: 'http://localhost:8082/realms/company',
      clientId: 'gateway-portal',
      appOrigin: 'http://localhost:4200',
      secureRoutes: ['http://localhost:8080'],
      production: false,
    });

    expect(oidcClient.provideAuth).toHaveBeenCalledWith({
      config: {
        authority: 'http://localhost:8082/realms/company',
        clientId: 'gateway-portal',
        redirectUrl: 'http://localhost:4200',
        postLogoutRedirectUri: 'http://localhost:4200',
        scope: 'openid profile',
        responseType: 'code',
        silentRenew: true,
        useRefreshToken: true,
        secureRoutes: ['http://localhost:8080'],
        logLevel: LogLevel.Debug,
      },
    });
  });

  it('lowers the log level to Warn in production', () => {
    provideKeycloakAuth({
      issuer: 'http://localhost:8082/realms/company',
      clientId: 'gateway-portal',
      appOrigin: 'http://localhost:4200',
      secureRoutes: [],
      production: true,
    });

    expect(oidcClient.provideAuth).toHaveBeenCalledWith(
      expect.objectContaining({ config: expect.objectContaining({ logLevel: LogLevel.Warn }) }),
    );
  });
});
