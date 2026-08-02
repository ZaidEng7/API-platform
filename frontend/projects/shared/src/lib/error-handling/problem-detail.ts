/**
 * Matches the exact RFC 7807 shape every backend service returns —
 * shared/common-web's GlobalExceptionHandler (guide §11.2). `errors` is
 * only present on 400 VALIDATION_FAILED responses.
 */
export interface ProblemDetail {
  readonly type?: string;
  readonly title?: string;
  readonly status?: number;
  readonly detail?: string;
  readonly instance?: string;
  readonly correlationId?: string;
  readonly timestamp?: string;
  readonly errorCode?: string;
  readonly errors?: readonly { field: string; code: string; message: string }[];
}
