import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Subject, of } from 'rxjs';
import { OidcSecurityService, type LoginResponse } from 'angular-auth-oidc-client';
import { App } from './app';

describe('App', () => {
  let checkAuth$: Subject<LoginResponse>;
  let oidcSecurityService: {
    checkAuth: ReturnType<typeof vi.fn>;
    authorize: ReturnType<typeof vi.fn>;
    logoff: ReturnType<typeof vi.fn>;
  };

  function loginResponse(overrides: Partial<LoginResponse>): LoginResponse {
    return {
      isAuthenticated: false,
      userData: undefined,
      accessToken: '',
      idToken: '',
      ...overrides,
    };
  }

  beforeEach(async () => {
    checkAuth$ = new Subject<LoginResponse>();
    oidcSecurityService = {
      checkAuth: vi.fn(() => checkAuth$),
      authorize: vi.fn(),
      logoff: vi.fn(() => of(undefined)),
    };

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: OidcSecurityService, useValue: oidcSecurityService },
      ],
    }).compileComponents();
  });

  it('shows a loading spinner while the auth check is in flight', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('lib-loading-spinner')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.signed-out')).toBeNull();
    expect(fixture.nativeElement.querySelector('lib-app-shell')).toBeNull();
  });

  it('shows a sign-in prompt when the user is not authenticated', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    checkAuth$.next(loginResponse({ isAuthenticated: false }));
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.signed-out h1')?.textContent).toContain('Client Portal');
    expect(el.querySelector('lib-app-shell')).toBeNull();

    (el.querySelector('.signed-out button') as HTMLButtonElement).click();
    expect(oidcSecurityService.authorize).toHaveBeenCalledOnce();
  });

  it('shows the app shell with the resolved display name once authenticated', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    checkAuth$.next(
      loginResponse({ isAuthenticated: true, userData: { preferred_username: 'jane.doe' } }),
    );
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('lib-app-shell')).toBeTruthy();
    expect(el.querySelector('.signed-out')).toBeNull();
    expect(el.textContent).toContain('jane.doe');
  });

  it('logs off through OidcSecurityService when "Log out" is clicked in the shell', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    checkAuth$.next(
      loginResponse({ isAuthenticated: true, userData: { preferred_username: 'jane.doe' } }),
    );
    fixture.detectChanges();

    // MatMenu renders its content into the CDK overlay, not as a child of
    // this component's own element, so open the menu first before querying
    // the document for the menu item.
    (fixture.nativeElement.querySelector('button[mat-button]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const menuItem = Array.from(document.querySelectorAll('.mat-mdc-menu-item')).find((el) =>
      el.textContent?.includes('Log out'),
    ) as HTMLButtonElement | undefined;
    menuItem?.click();

    expect(oidcSecurityService.logoff).toHaveBeenCalledOnce();
  });
});
