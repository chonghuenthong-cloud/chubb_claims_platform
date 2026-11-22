import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, of, shareReplay, tap } from 'rxjs';

export type Role = 'CLAIMANT' | 'STAFF';

export interface DevUser {
  id: string;
  fullName: string;
  role: Role;
}

const STORAGE_KEY = 'viewingAsUserId';

/**
 * Who the app is acting as.
 *
 * TEMPORARY STUB: the "Viewing as" picker sets the user, and every API call sends their id in the
 * X-User-Id header. JWT sign-in replaces this; components only read `user()`, so they won't change.
 */
@Injectable({ providedIn: 'root' })
export class CurrentUserService {
  private readonly http = inject(HttpClient);

  /** Seeded users from the backend's dev endpoint. */
  readonly users = signal<DevUser[]>([]);
  private readonly selectedId = signal<string | null>(readStoredId());

  /** The user being viewed as; null until the user list has loaded. */
  readonly user = computed(() => this.users().find((u) => u.id === this.selectedId()) ?? null);

  /** Loads the user list once (shared by every caller); keeps the previous selection if it still exists, else picks the first. */
  private readonly load$ = this.http.get<DevUser[]>('/api/dev/users').pipe(
    tap((users) => {
      this.users.set(users);
      if (!users.some((u) => u.id === this.selectedId())) {
        this.select(users[0]?.id ?? null);
      }
    }),
    shareReplay(1),
  );

  /** Resolves to the current user, loading the list first if needed. Used by route guards. */
  ensureLoaded(): Observable<DevUser | null> {
    return this.users().length ? of(this.user()) : this.load$.pipe(map(() => this.user()));
  }

  select(id: string | null): void {
    this.selectedId.set(id);
    // Remembered across refreshes; storage can be unavailable (private mode), which is fine
    try {
      if (id) localStorage.setItem(STORAGE_KEY, id);
    } catch {}
  }
}

function readStoredId(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}
