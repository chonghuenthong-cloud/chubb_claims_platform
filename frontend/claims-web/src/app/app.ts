import { Component, computed, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSelectModule } from '@angular/material/select';
import { CurrentUserService } from './core/current-user.service';
import { homeFor } from './core/role.guard';
import { NotificationBell } from './core/notification-bell';

/**
 * App shell: top bar with the "Viewing as" picker.
 * TEMPORARY: the picker stands in for sign-in; the chosen user's role decides which screens show.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, MatToolbarModule, MatSelectModule, NotificationBell],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly currentUser = inject(CurrentUserService);
  private readonly router = inject(Router);

  protected readonly claimants = computed(() => this.currentUser.users().filter((u) => u.role === 'CLAIMANT'));
  protected readonly staff = computed(() => this.currentUser.users().filter((u) => u.role === 'STAFF'));

  /** Switching user opens that user's landing page. */
  protected viewAs(userId: string): void {
    this.currentUser.select(userId);
    this.router.navigateByUrl(homeFor(this.currentUser.user()?.role));
  }
}
