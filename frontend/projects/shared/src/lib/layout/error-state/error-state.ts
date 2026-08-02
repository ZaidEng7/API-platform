import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/** Inline error display for a page/section that failed to load, with an optional retry action. */
@Component({
  selector: 'lib-error-state',
  imports: [MatButtonModule, MatIconModule],
  template: `
    <div class="error-state">
      <mat-icon class="error-state__icon">error_outline</mat-icon>
      <p class="error-state__message">{{ message() }}</p>
      @if (retryable()) {
        <button mat-stroked-button (click)="retry.emit()">Retry</button>
      }
    </div>
  `,
  styles: `
    .error-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 0.75rem;
      padding: 2rem;
      text-align: center;
    }

    .error-state__icon {
      color: var(--mat-sys-error);
      font-size: 2.5rem;
      width: 2.5rem;
      height: 2.5rem;
    }

    .error-state__message {
      color: var(--mat-sys-on-surface-variant);
      margin: 0;
      max-width: 32rem;
    }
  `,
})
export class ErrorState {
  readonly message = input('Something went wrong. Please try again.');
  readonly retryable = input(true);
  readonly retry = output<void>();
}
