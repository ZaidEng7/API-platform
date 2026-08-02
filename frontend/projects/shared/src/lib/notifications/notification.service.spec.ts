import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;
  let snackBar: { open: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    snackBar = { open: vi.fn() };
    TestBed.configureTestingModule({
      providers: [NotificationService, { provide: MatSnackBar, useValue: snackBar }],
    });
    service = TestBed.inject(NotificationService);
  });

  it('shows an error notification with a Dismiss action and the error styling', () => {
    service.showError('Something broke.');

    expect(snackBar.open).toHaveBeenCalledWith('Something broke.', 'Dismiss', {
      duration: 8000,
      panelClass: 'notification--error',
    });
  });

  it('shows a success notification with no action and the success styling', () => {
    service.showSuccess('Saved.');

    expect(snackBar.open).toHaveBeenCalledWith('Saved.', undefined, {
      duration: 4000,
      panelClass: 'notification--success',
    });
  });
});
