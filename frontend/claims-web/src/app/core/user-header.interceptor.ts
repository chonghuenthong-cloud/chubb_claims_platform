import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { CurrentUserService } from './current-user.service';

/** TEMPORARY STUB: adds the X-User-Id header to API calls. Replaced by a JWT Authorization header later. */
export const userHeaderInterceptor: HttpInterceptorFn = (req, next) => {
  const user = inject(CurrentUserService).user();
  if (!user || !req.url.startsWith('/api/')) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { 'X-User-Id': user.id } }));
};
