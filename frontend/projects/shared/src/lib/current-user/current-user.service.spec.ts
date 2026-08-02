import { TestBed } from '@angular/core/testing';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { of } from 'rxjs';
import { CurrentUserService } from './current-user.service';

describe('CurrentUserService', () => {
  function setup(payload: unknown) {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: OidcSecurityService,
          useValue: { getPayloadFromAccessToken: () => of(payload) },
        },
      ],
    });
    return TestBed.inject(CurrentUserService);
  }

  it('exposes the sub claim from the access token payload', () => {
    const service = setup({ sub: 'a1b2c3d4-0000-0000-0000-000000000000' });

    expect(service.subjectId()).toBe('a1b2c3d4-0000-0000-0000-000000000000');
  });

  it('exposes null when the payload has no sub claim', () => {
    const service = setup({});

    expect(service.subjectId()).toBeNull();
  });

  it('exposes realm roles from the realm_access claim', () => {
    const service = setup({ realm_access: { roles: ['compliance', 'operations'] } });

    expect(service.roles()).toEqual(['compliance', 'operations']);
  });

  it('exposes an empty roles array when the payload has no realm_access claim', () => {
    const service = setup({});

    expect(service.roles()).toEqual([]);
  });
});
