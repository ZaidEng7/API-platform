import { Routes } from '@angular/router';

// Feature routes land in Phase B. No per-route auth guard is needed here —
// App (app.ts) already gates the entire UI behind isAuthenticated() before
// the router-outlet is ever rendered, since every Client Portal view is
// investor-only (unlike Admin Portal, where different sections may end up
// gated to different staff roles).
export const routes: Routes = [];
