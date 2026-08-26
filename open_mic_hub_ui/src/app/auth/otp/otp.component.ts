import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ToastrService } from 'ngx-toastr';
import { environment } from '../../environment/environment';

@Component({
  selector: 'app-otp',
  standalone:false,
  templateUrl: './otp.component.html',
})
export class OtpComponent implements OnInit {
  otpForm: FormGroup;
  submitted = false;
  errorMessage = '';
  isSubmitting = false;
  email: string = '';

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient,
    private toast: ToastrService
  ) {
    this.otpForm = this.fb.group({
      otp: ['', [Validators.required, Validators.pattern('^[0-9]{6}$')]]
    });
  }

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      this.email = params['email'] || '';
      if (!this.email) {
        this.errorMessage = 'Email parameter is missing. Please use the verification link from your email.';
      }
    });
  }

  onSubmit() {
  this.submitted = true;
  this.errorMessage = '';

  if (this.otpForm.invalid) {
    return;
  }

  this.isSubmitting = true;

  const otpValue = this.otpForm.get('otp')?.value;

  // Append email and token (otp) as query params
  const url = `${environment.baseUrl}/auth/verify-email?email=${encodeURIComponent(this.email)}&token=${encodeURIComponent(otpValue)}`;

  // Since all params are in URL, body can be empty or null
  this.http.post(url, null).subscribe({
    next: (res: any) => {
      this.isSubmitting = false;
      this.toast.success('OTP verified successfully. You can now login.');
      this.router.navigate(['/auth/login']);
    },
    error: (err) => {
      this.isSubmitting = false;
      this.errorMessage = err.error?.message || 'OTP verification failed. Please try again.';
    }
  });
}


}
