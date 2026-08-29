import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { PublicNavComponent } from './public-nav.component';
import { commonTestProviders } from '../../testing/test-providers';

/**
 * The bar that keeps the public pages from being dead ends.
 *
 * What it offers has to follow the session, because the two states need opposite things: a visitor
 * needs a way in, and someone already signed in needs their own area rather than an invitation to
 * sign in again.
 */
describe('PublicNavComponent', () => {
  let fixture: ComponentFixture<PublicNavComponent>;
  let component: PublicNavComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PublicNavComponent],
      providers: [...commonTestProviders],
    }).compileComponents();

    fixture = TestBed.createComponent(PublicNavComponent);
    component = fixture.componentInstance;
    localStorage.clear();
  });

  afterEach(() => localStorage.clear());

  it('offers a way in when nobody is signed in', () => {
    component.ngOnInit();
    fixture.detectChanges();

    expect(component.signedIn).toBeFalse();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Log in');
    expect(text).not.toContain('Sign out');
  });

  it('points an organizer at their own area instead', () => {
    localStorage.setItem('authToken', 'a-token');
    localStorage.setItem('urole', JSON.stringify(['ORGANIZER']));

    component.ngOnInit();
    fixture.detectChanges();

    expect(component.signedIn).toBeTrue();
    expect(component.homeLink).toBe('/user');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Sign out');
  });

  it('points an artist at the artist area', () => {
    localStorage.setItem('authToken', 'a-token');
    localStorage.setItem('urole', JSON.stringify(['ARTIST']));

    component.ngOnInit();

    expect(component.homeLink).toBe('/artist/');
  });

  it('points an admin at the admin area', () => {
    localStorage.setItem('authToken', 'a-token');
    localStorage.setItem('urole', JSON.stringify(['ADMIN']));

    component.ngOnInit();

    expect(component.homeLink).toBe('/admin');
  });

  it('survives a stored role list that is not valid JSON', () => {
    // Storage can hold anything; this must not take the page down with it.
    localStorage.setItem('authToken', 'a-token');
    localStorage.setItem('urole', 'not json');

    expect(() => component.ngOnInit()).not.toThrow();
    expect(component.homeLink).toBe('/');
  });

  it('clears the session and returns home on sign out', () => {
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigate');
    localStorage.setItem('authToken', 'a-token');
    localStorage.setItem('urole', JSON.stringify(['ORGANIZER']));
    component.ngOnInit();

    component.signOut();

    expect(localStorage.getItem('authToken')).toBeNull();
    expect(localStorage.getItem('urole')).toBeNull();
    expect(component.signedIn).toBeFalse();
    expect(navigate).toHaveBeenCalledWith(['/']);
  });
});
