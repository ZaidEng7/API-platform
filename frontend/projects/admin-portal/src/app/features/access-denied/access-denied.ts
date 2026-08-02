import { Component } from '@angular/core';

/** Landed on when a staff role has no accessible section — see default-redirect.guard.ts and role.guard.ts. */
@Component({
  selector: 'app-access-denied',
  imports: [],
  templateUrl: './access-denied.html',
})
export class AccessDenied {}
