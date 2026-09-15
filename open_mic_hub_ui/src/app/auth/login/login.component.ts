import { Component, OnInit, inject } from '@angular/core';
import { Role } from '../../shared/role';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../auth.service';
import { ToastrService } from 'ngx-toastr';
import { environment } from '../../environment/environment';
import { DiscoveryService } from '../../discovery/discovery.service';

@Component({
  selector: 'app-login',
  standalone: false,
  templateUrl: './login.component.html',
})
export class LoginComponent implements OnInit {

  readonly currentYear = new Date().getFullYear();

  // Filled from the backend, which is the only place that knows whether a provider's credentials
  // are set. Empty until it answers, and stays empty if it cannot — a missing button is a better
  // failure than one that leads nowhere.
  socialProviders: string[] = [];

  loginError: boolean = false;
  loginForm: any;
  isLoading: boolean = false;
  showPassword: boolean = false;

  private readonly discovery = inject(DiscoveryService);

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService,
    private toast: ToastrService
  ) {}

  /** The path Spring listens on to begin a provider's handshake. */
  signInUrl(provider: string): string {
    return `${environment.host}/oauth2/authorization/${provider}`;
  }

  /** Capitalised for display; the ids come back lowercase. */
  providerLabel(provider: string): string {
    return provider.charAt(0).toUpperCase() + provider.slice(1);
  }

  /** Bootstrap icon name per provider, falling back to a generic one. */
  providerIcon(provider: string): string {
    return provider === 'google' ? 'bi-google'
      : provider === 'facebook' ? 'bi-facebook'
      : 'bi-box-arrow-in-right';
  }

  private loadSocialProviders(): void {
    this.authService.socialProviders().subscribe({
      next: (response: any) => {
        this.socialProviders = response?.data?.providers ?? [];
      },
      error: () => {
        // Social sign-in simply is not offered. Password sign-in is unaffected, and there is
        // nothing here worth interrupting someone with a toast about.
        this.socialProviders = [];
      },
    });
  }

  ngOnInit(): void {
    this.loadSocialProviders();
    this.loginForm = new FormGroup({
      email: new FormControl('', [Validators.required, Validators.email]),
      password: new FormControl('', [Validators.required, Validators.minLength(6)])
    });
  }

  togglePasswordVisibility(): void {
    this.showPassword = !this.showPassword;
  }

  onLogin(): void {
    if (this.loginForm.valid) {
      this.isLoading = true;
      this.loginError = false;
      
      this.authService.login(this.loginForm.value).subscribe(
        (response: any) => {
          this.isLoading = false;
          
          if (response && response.data && response.data.access_token) {
            const token = response.data.access_token;
            const roles = response.data.user_role.map((r: any) => r.role);
    
            localStorage.setItem('authToken', token);
            localStorage.setItem('urole', JSON.stringify(roles));

            // What they searched for and opened before signing in now belongs to the account, so
            // the first page they land on already reflects it.
            this.discovery.claimVisitorHistory().then(() => this.redirectUser(roles));
          }
        },
        (error) => {
             this.toast.error(error.error.message);
          this.isLoading = false;
          this.loginError = true;
        }
      );
    } else {
      this.loginForm.markAllAsTouched();
    }
  }

  /**
   * Sends each role to the area it belongs in.
   *
   * Most specific first: a super admin also matches nothing else here, but the
   * ordering matters once an account can hold more than one role.
   */
  private redirectUser(roles: string[]): void {
    // Came here from something that needed an account — a booking request, say.
    // Send them back to finish it rather than to their dashboard.
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    if (returnUrl && returnUrl.startsWith('/')) {
      // Relative paths only: an absolute URL here would let a crafted link
      // bounce someone off the site straight after they sign in.
      this.router.navigateByUrl(returnUrl);
      return;
    }

    if (roles.includes(Role.SUPER_ADMIN) || roles.includes(Role.ADMIN)) {
      this.router.navigate(['/admin']);
    } else if (roles.includes(Role.ARTIST)) {
      this.router.navigate(['/artist/']);
    } else if (roles.includes(Role.ORGANIZER) || roles.includes(Role.USER)) {
      this.router.navigate(['/user']);
    } else {
      this.router.navigate(['/']);
    }
  }
}