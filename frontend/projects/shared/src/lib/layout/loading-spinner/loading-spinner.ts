import { Component, input } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

/** Centered spinner for a page/section still loading data. */
@Component({
  selector: 'lib-loading-spinner',
  imports: [MatProgressSpinnerModule],
  template: `
    <div class="loading-spinner">
      <mat-spinner [diameter]="diameter()" />
      @if (message()) {
        <p class="loading-spinner__message">{{ message() }}</p>
      }
    </div>
  `,
  styles: `
    .loading-spinner {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 1rem;
      padding: 2rem;
    }

    .loading-spinner__message {
      color: var(--mat-sys-on-surface-variant);
      margin: 0;
    }
  `,
})
export class LoadingSpinner {
  readonly diameter = input(48);
  readonly message = input<string>();
}
