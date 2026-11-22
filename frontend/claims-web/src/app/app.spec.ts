import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { CurrentUserService, DevUser } from './core/current-user.service';

describe('App', () => {
  const users: DevUser[] = [
    { id: 'c1', fullName: 'Alice Tan', role: 'CLAIMANT' },
    { id: 's1', fullName: 'Priya Nair', role: 'STAFF' },
  ];

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('shows the "Viewing as" picker with the first user once users have loaded', async () => {
    const fixture = TestBed.createComponent(App);
    TestBed.inject(CurrentUserService).ensureLoaded().subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/dev/users').flush(users);
    await fixture.whenStable();

    const picker = fixture.nativeElement.querySelector('mat-select') as HTMLElement;
    expect(picker.textContent).toContain('Alice Tan');
  });

  it('hides the picker until users have loaded', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('mat-select')).toBeNull();
  });
});
