import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LoadingSpinner } from './loading-spinner';

describe('LoadingSpinner', () => {
  let fixture: ComponentFixture<LoadingSpinner>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [LoadingSpinner] }).compileComponents();
    fixture = TestBed.createComponent(LoadingSpinner);
  });

  it('defaults the diameter to 48 and shows no message', () => {
    fixture.detectChanges();

    expect(fixture.componentInstance.diameter()).toBe(48);
    expect(fixture.nativeElement.querySelector('.loading-spinner__message')).toBeNull();
  });

  it('shows the message when provided', () => {
    fixture.componentRef.setInput('message', 'Loading your portfolio…');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.loading-spinner__message')?.textContent).toContain(
      'Loading your portfolio…',
    );
  });

  it('reflects a custom diameter input', () => {
    fixture.componentRef.setInput('diameter', 24);
    fixture.detectChanges();

    expect(fixture.componentInstance.diameter()).toBe(24);
  });
});
