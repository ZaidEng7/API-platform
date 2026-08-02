import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { Observable } from 'rxjs';
import { CurrentUserService, ErrorState, LoadingSpinner, ReportingApiClient } from 'shared';
import { DocumentReviewStore } from './document-review.store';

/**
 * Cross-customer document review queue (Reporting Service's read-model).
 * `verify`/`reject` are both `hasRole('COMPLIANCE')` only on Document
 * Service — same single-role gate as KYC's `/decision`, just two actions
 * instead of one.
 */
@Component({
  selector: 'app-document-review',
  imports: [DatePipe, FormsModule, MatButtonModule, MatChipsModule, LoadingSpinner, ErrorState],
  providers: [DocumentReviewStore],
  templateUrl: './document-review.html',
  styleUrl: './document-review.scss',
})
export class DocumentReview implements OnInit {
  protected readonly store = inject(DocumentReviewStore);
  private readonly currentUserService = inject(CurrentUserService);

  protected readonly notes: Record<string, string> = {};
  protected readonly busyIds = signal<ReadonlySet<string>>(new Set());
  protected readonly canReview = () => this.currentUserService.roles().includes('compliance');

  ngOnInit(): void {
    this.store.load();
  }

  protected reload(): void {
    this.store.load();
  }

  protected statusClass(
    status: ReportingApiClient.DocumentReportResponse.StatusEnum | undefined,
  ): string {
    switch (status) {
      case 'VERIFIED':
        return 'status--verified';
      case 'REJECTED':
        return 'status--rejected';
      default:
        return 'status--uploaded';
    }
  }

  protected verify(documentId: string): void {
    this.decide(documentId, (notes) => this.store.verify(documentId, notes));
  }

  protected reject(documentId: string): void {
    this.decide(documentId, (notes) => this.store.reject(documentId, notes));
  }

  private decide(documentId: string, action: (notes: string) => Observable<unknown>): void {
    const notes = this.notes[documentId]?.trim();
    if (!notes || this.busyIds().has(documentId)) {
      return;
    }

    this.setBusy(documentId, true);
    action(notes).subscribe({
      next: () => {
        this.setBusy(documentId, false);
        this.store.load();
      },
      error: () => this.setBusy(documentId, false),
    });
  }

  private setBusy(documentId: string, busy: boolean): void {
    this.busyIds.update((current) => {
      const next = new Set(current);
      if (busy) {
        next.add(documentId);
      } else {
        next.delete(documentId);
      }
      return next;
    });
  }
}
