import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { of, throwError } from 'rxjs';
import { AuthService } from '../auth.service';
import { environment } from '../../environment/environment';
import { LoginComponent } from './login.component';

/**
 * Which social buttons the sign-in page offers.
 *
 * The list comes from the backend because it is the only side that knows which credentials are
 * set. A hardcoded flag here was wrong in both directions at once: a configured Google client
 * showed no button, and a Facebook button appeared for a deployment that had never set Facebook up.
 */
describe('LoginComponent social sign-in', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authService = jasmine.createSpyObj('AuthService', ['login', 'socialProviders']);
    authService.socialProviders.and.returnValue(of({ data: { providers: ['google'] } }));

    await TestBed.configureTestingModule({
      declarations: [LoginComponent],
      imports: [ReactiveFormsModule],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: jasmine.createSpyObj('Router', ['navigate', 'navigateByUrl']) },
        { provide: ToastrService, useValue: jasmine.createSpyObj('ToastrService', ['error']) },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
      ],
    }).overrideTemplate(LoginComponent, '').compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
  });

  it('offers only the providers the backend reports', () => {
    component.ngOnInit();

    expect(component.socialProviders).toEqual(['google']);
  });

  it('offers both when both are configured', () => {
    authService.socialProviders.and.returnValue(
      of({ data: { providers: ['facebook', 'google'] } }));

    component.ngOnInit();

    expect(component.socialProviders).toEqual(['facebook', 'google']);
  });

  it('offers none when social sign-in is off', () => {
    authService.socialProviders.and.returnValue(of({ data: { providers: [] } }));

    component.ngOnInit();

    expect(component.socialProviders).toEqual([]);
  });

  it('offers none when the backend cannot be reached', () => {
    // A missing button is a better failure than one that leads nowhere.
    authService.socialProviders.and.returnValue(throwError(() => new Error('network')));

    component.ngOnInit();

    expect(component.socialProviders).toEqual([]);
  });

  it('points each button at the handshake path the backend listens on', () => {
    expect(component.signInUrl('google')).toBe(`${environment.host}/oauth2/authorization/google`);
    expect(component.signInUrl('facebook')).toBe(`${environment.host}/oauth2/authorization/facebook`);
  });

  it('labels and icons each provider recognisably', () => {
    expect(component.providerLabel('google')).toBe('Google');
    expect(component.providerIcon('google')).toBe('bi-google');
    expect(component.providerIcon('facebook')).toBe('bi-facebook');
    // An unrecognised provider still renders rather than breaking the row.
    expect(component.providerIcon('gitlab')).toBe('bi-box-arrow-in-right');
  });
});
