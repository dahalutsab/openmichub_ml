import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { SocialComponent } from './social.component';
import { DiscoveryService } from '../../discovery/discovery.service';

/**
 * The landing point after a social sign-in.
 *
 * Everything here reads a URL fragment the component does not control, so the cases that matter
 * are the malformed ones. An empty role list in particular is not hypothetical: it is exactly what
 * arrived while the backend was building principals that had never been through account
 * provisioning, and treating it as success would have stored a token no request could use.
 */
describe('SocialComponent', () => {
  let fixture: ComponentFixture<SocialComponent>;
  let component: SocialComponent;
  let router: jasmine.SpyObj<Router>;
  let toast: jasmine.SpyObj<ToastrService>;
  let discovery: jasmine.SpyObj<DiscoveryService>;

  const setFragment = (fragment: string) =>
    history.replaceState(null, '', `${window.location.pathname}${fragment}`);

  beforeEach(async () => {
    router = jasmine.createSpyObj('Router', ['navigate', 'navigateByUrl']);
    toast = jasmine.createSpyObj('ToastrService', ['error']);
    discovery = jasmine.createSpyObj('DiscoveryService', { claimVisitorHistory: Promise.resolve() });

    await TestBed.configureTestingModule({
      declarations: [SocialComponent],
      providers: [
        { provide: Router, useValue: router },
        { provide: ToastrService, useValue: toast },
        { provide: DiscoveryService, useValue: discovery },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: () => null } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SocialComponent);
    component = fixture.componentInstance;
    localStorage.clear();
  });

  afterEach(() => {
    history.replaceState(null, '', window.location.pathname);
    localStorage.clear();
  });

  it('stores the token and roles, then routes by role', () => {
    setFragment('#token=abc.def&roles=ORGANIZER&onboarding=false&expiresIn=9000');

    component.ngOnInit();

    expect(localStorage.getItem('authToken')).toBe('abc.def');
    expect(JSON.parse(localStorage.getItem('urole')!)).toEqual(['ORGANIZER']);
    expect(router.navigate).toHaveBeenCalledWith(['/user']);
  });

  it('carries what this browser did while signed out over to the account', () => {
    setFragment('#token=abc&roles=ORGANIZER&onboarding=false');

    component.ngOnInit();

    expect(discovery.claimVisitorHistory).toHaveBeenCalledTimes(1);
  });

  it('claims nothing when sign-in failed', () => {
    setFragment('#error=Nope');

    component.ngOnInit();

    expect(discovery.claimVisitorHistory).not.toHaveBeenCalled();
  });

  it('sends an artist to the artist area', () => {
    setFragment('#token=abc&roles=ARTIST&onboarding=false');

    component.ngOnInit();

    expect(router.navigate).toHaveBeenCalledWith(['/artist/']);
  });

  it('sends an admin to the admin area', () => {
    setFragment('#token=abc&roles=ADMIN&onboarding=false');

    component.ngOnInit();

    expect(router.navigate).toHaveBeenCalledWith(['/admin']);
  });

  it('clears the token out of the address bar', () => {
    setFragment('#token=secret-token&roles=ORGANIZER&onboarding=false');

    component.ngOnInit();

    // Otherwise the token sits in browser history for anyone with the machine.
    expect(window.location.hash).toBe('');
  });

  it('sends a brand-new account to set itself up first', () => {
    setFragment('#token=abc&roles=ORGANIZER&onboarding=true');

    component.ngOnInit();

    expect(router.navigate).toHaveBeenCalledWith(['/auth/complete-profile']);
    // Still signed in — the account exists, it just has not said what it is for.
    expect(localStorage.getItem('authToken')).toBe('abc');
  });

  it('shows the error the backend sent, and stores nothing', () => {
    setFragment('#error=Verify%20your%20email%20address%20with%20GOOGLE%20first.');

    component.ngOnInit();

    expect(component.error).toBe('Verify your email address with GOOGLE first.');
    expect(localStorage.getItem('authToken')).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/auth/login']);
  });

  it('refuses a token that arrives with no roles', () => {
    // The shape produced when a principal never reached account provisioning: a token exists but
    // there is no account behind it, so storing it would leave the app signed in to nothing.
    setFragment('#token=abc&roles=&onboarding=false');

    component.ngOnInit();

    expect(localStorage.getItem('authToken')).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/auth/login']);
  });

  it('refuses an empty fragment', () => {
    setFragment('');

    component.ngOnInit();

    expect(localStorage.getItem('authToken')).toBeNull();
    expect(toast.error).toHaveBeenCalled();
  });
});
