import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { NavigationEnd, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatBadgeModule } from '@angular/material/badge';
import { MatMenuModule } from '@angular/material/menu';
import { CurrentUserService } from './current-user.service';

interface NotificationItem {
  id: string;
  claimId: string;
  claimNumber: string;
  message: string;
  createdAt: string;
  read: boolean;
}

interface NotificationsResponse {
  unreadCount: number;
  items: NotificationItem[];
}

/**
 * Claimant's bell: unread count and the latest notifications.
 * Notifications are created asynchronously by the Kafka notification consumer; the bell refreshes when the
 * page loads, on every navigation and when the viewed user changes (no live push, as decided).
 */
@Component({
  selector: 'app-notification-bell',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatBadgeModule, MatMenuModule],
  template: `
    <button mat-icon-button [matMenuTriggerFor]="menu" aria-label="Notifications" class="bell"
            [matBadge]="unread()" [matBadgeHidden]="!unread()" matBadgeColor="warn">
      <mat-icon>notifications</mat-icon>
    </button>
    <mat-menu #menu="matMenu" xPosition="before" class="notification-menu">
      @if (unread()) {
        <button mat-menu-item (click)="markAllRead()">Mark all as read</button>
      }
      @for (n of items(); track n.id) {
        <button mat-menu-item (click)="open(n)" [class.unread]="!n.read" class="item">
          <span class="message">{{ n.message }}</span>
          <span class="time">{{ n.createdAt | date: 'd MMM, HH:mm' }}</span>
        </button>
      } @empty {
        <p class="empty">No notifications</p>
      }
    </mat-menu>
  `,
  styles: `
    .bell { color: var(--mat-sys-on-primary); }
    .item { height: auto; min-height: 48px; line-height: 1.3; padding-top: 8px; padding-bottom: 8px; }
    .message { display: block; white-space: normal; }
    .time { display: block; font: var(--mat-sys-body-small); color: var(--mat-sys-on-surface-variant); }
    .unread .message { font-weight: 500; }
    .empty { margin: 8px 16px; color: var(--mat-sys-on-surface-variant); }
  `,
})
export class NotificationBell {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly currentUser = inject(CurrentUserService);

  private readonly data = signal<NotificationsResponse | null>(null);
  protected readonly unread = computed(() => this.data()?.unreadCount ?? 0);
  protected readonly items = computed(() => this.data()?.items ?? []);

  constructor() {
    // Reload when the viewed user changes (and on first render)
    effect(() => {
      if (this.currentUser.user()?.role === 'CLAIMANT') this.refresh();
    });
    // ...and after every navigation, so new notifications show up as the claimant moves around
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.refresh());
  }

  /** Marks the notification read and opens its claim. */
  protected open(n: NotificationItem): void {
    this.http.post(`/api/me/notifications/${n.id}/read`, null).subscribe(() => this.refresh());
    this.router.navigate(['/claimant/claims', n.claimId]);
  }

  protected markAllRead(): void {
    this.http.post('/api/me/notifications/read-all', null).subscribe(() => this.refresh());
  }

  private refresh(): void {
    this.http.get<NotificationsResponse>('/api/me/notifications').subscribe((d) => this.data.set(d));
  }
}
