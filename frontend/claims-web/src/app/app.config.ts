import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatIconRegistry } from '@angular/material/icon';
import { routes } from './app.routes';
import { userHeaderInterceptor } from './core/user-header.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([userHeaderInterceptor])),
    // Date picker uses plain JavaScript Date objects
    provideNativeDateAdapter(),
    // <mat-icon> defaults to the "Material Icons" font, but index.html loads "Material Symbols Outlined";
    // without this, icons render as blank space
    provideAppInitializer(() => {
      inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined');
    }),
  ],
};
