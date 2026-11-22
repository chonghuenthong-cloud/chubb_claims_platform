import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';

/** Staff area tabs: Work queue (landing page) and Team dashboard. */
@Component({
  selector: 'app-staff-tabs',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatTabsModule],
  template: `
    <nav mat-tab-nav-bar [tabPanel]="panel" class="tabs">
      <a mat-tab-link routerLink="/staff" [routerLinkActiveOptions]="{ exact: true }"
         routerLinkActive #queue="routerLinkActive" [active]="queue.isActive">Work queue</a>
      <a mat-tab-link routerLink="/staff/dashboard"
         routerLinkActive #dash="routerLinkActive" [active]="dash.isActive">Team dashboard</a>
    </nav>
    <mat-tab-nav-panel #panel>
      <router-outlet />
    </mat-tab-nav-panel>
  `,
  styles: `.tabs { margin: -8px 0 16px; }`,
})
export class StaffTabs {}
