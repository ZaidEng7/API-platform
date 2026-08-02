import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/** Thin wrapper over MatSnackBar so both apps get consistent notification styling/duration. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  showError(message: string): void {
    this.snackBar.open(message, 'Dismiss', {
      duration: 8000,
      panelClass: 'notification--error',
    });
  }

  showSuccess(message: string): void {
    this.snackBar.open(message, undefined, {
      duration: 4000,
      panelClass: 'notification--success',
    });
  }
}
