import { Component, OnInit } from '@angular/core';
import { Role } from '../../shared/role';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-login',
  standalone: false,
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent implements OnInit {

  loginError: boolean = false;
  loginForm: any;
  isLoading: boolean = false;
  showPassword: boolean = false;

  constructor(
    private router: Router,
    private authService: AuthService,
    private toast: ToastrService
  ) {}

  ngOnInit(): void {
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
            
            this.redirectUser(roles);
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