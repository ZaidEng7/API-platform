import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { AppShell, type NavLink } from './app-shell';

describe('AppShell', () => {
  let fixture: ComponentFixture<AppShell>;

  const navLinks: NavLink[] = [
    { label: 'Portfolio', path: '/portfolio', icon: 'pie_chart' },
    { label: 'Subscriptions', path: '/subscriptions', icon: 'receipt_long' },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [provideRouter([]), provideNoopAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(AppShell);
    fixture.componentRef.setInput('appTitle', 'Client Portal');
  });

  it('renders the app title', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.shell__title')?.textContent).toContain(
      'Client Portal',
    );
  });

  it('renders one nav link per entry, in order', () => {
    fixture.componentRef.setInput('navLinks', navLinks);
    fixture.detectChanges();

    const links: NodeListOf<HTMLElement> =
      fixture.nativeElement.querySelectorAll('.shell__nav-link');
    expect(links.length).toBe(2);
    expect(links[0].textContent).toContain('Portfolio');
    expect(links[1].textContent).toContain('Subscriptions');
  });

  it('renders no nav links when none are given', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.shell__nav-link').length).toBe(0);
  });

  it('does not show the user menu trigger when no display name is set', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[matMenuTriggerFor]')).toBeNull();
  });

  it('shows the user display name when set', () => {
    fixture.componentRef.setInput('userDisplayName', 'Jane Doe');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Jane Doe');
  });

  it('emits logout when "Log out" is clicked from the user menu', () => {
    fixture.componentRef.setInput('userDisplayName', 'Jane Doe');
    fixture.detectChanges();

    const logout = vi.fn();
    fixture.componentInstance.logout.subscribe(logout);

    // MatMenu renders its content into the CDK overlay, not as a child of
    // this component's own element, so open the menu first before querying
    // the document for the menu item.
    (fixture.nativeElement.querySelector('button[mat-button]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const menuItem = Array.from(document.querySelectorAll('.mat-mdc-menu-item')).find((el) =>
      el.textContent?.includes('Log out'),
    ) as HTMLButtonElement | undefined;
    menuItem?.click();

    expect(logout).toHaveBeenCalledOnce();
  });
});
