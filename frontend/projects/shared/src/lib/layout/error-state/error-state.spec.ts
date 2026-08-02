import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ErrorState } from './error-state';

describe('ErrorState', () => {
  let fixture: ComponentFixture<ErrorState>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ErrorState] }).compileComponents();
    fixture = TestBed.createComponent(ErrorState);
  });

  it('shows the default message and a retry button by default', () => {
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('.error-state__message')?.textContent).toContain(
      'Something went wrong. Please try again.',
    );
    expect(el.querySelector('button')).toBeTruthy();
  });

  it('shows a custom message when provided', () => {
    fixture.componentRef.setInput('message', 'Could not load your portfolio.');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.error-state__message')?.textContent).toContain(
      'Could not load your portfolio.',
    );
  });

  it('hides the retry button when not retryable', () => {
    fixture.componentRef.setInput('retryable', false);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });

  it('emits retry when the retry button is clicked', () => {
    fixture.detectChanges();
    const retry = vi.fn();
    fixture.componentInstance.retry.subscribe(retry);

    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();

    expect(retry).toHaveBeenCalledOnce();
  });
});
