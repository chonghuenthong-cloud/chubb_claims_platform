import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { map } from 'rxjs';
import { CurrentUserService, Role } from './current-user.service';

/** Each role's landing page. */
export function homeFor(role: Role | undefined): string {
  return role === 'STAFF' ? '/staff' : '/claimant';
}

/** Lets the route open only for the given role; anyone else is sent to their own landing page. */
export function roleGuard(role: Role): CanActivateFn {
  return () => {
    const router = inject(Router);
    return inject(CurrentUserService).ensureLoaded().pipe(
      map((user): boolean | UrlTree => user?.role === role || router.parseUrl(homeFor(user?.role))),
    );
  };
}

/** Sends "/" to the current user's landing page. */
export const homeRedirect: CanActivateFn = () => {
  const router = inject(Router);
  return inject(CurrentUserService).ensureLoaded().pipe(map((user) => router.parseUrl(homeFor(user?.role))));
};
