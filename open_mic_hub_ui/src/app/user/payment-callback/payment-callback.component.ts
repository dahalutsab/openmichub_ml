import { Component, NgZone, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { UserService } from '../user.service';
import { ToastService } from '../../auth/toastr.service';
import { apiMessage } from '../../shared/api-error';

@Component({
  selector: 'app-payment-callback',
  standalone: false,
  templateUrl: './payment-callback.component.html',
})
export class PaymentCallbackComponent implements OnInit {
  queryParams: any;

  constructor(
    private route: ActivatedRoute,
    private userService: UserService,
    private toast: ToastService,
    private router: Router,
    private ngZone: NgZone
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.queryParams = params;

      if (this.isComplete) {
        this.userService.confirmPaymentViaPost(params).subscribe({
          next: () => {
            this.toast.showSuccess('Payment successful!');
            this.ngZone.run(() => {
              this.router.navigate(['/user/bookings']);
            });
          },
          error: (err) => {
            console.error('Payment Callback Failed', err);
            // The server says why — a gateway that could not verify the payment is a
            // different problem from a payment that was declined.
            this.toast.showError(
              apiMessage(err, 'Payment processing failed on the server.'));
          }
        });
      }
    });
  }

  get isComplete(): boolean {
    return this.queryParams?.['status']?.toLowerCase() === 'completed';
  }

  /** The gateway reports paisa; the UI shows rupees. */
  get amount(): number {
    return Number(this.queryParams?.['amount'] ?? 0) / 100;
  }

  goToBookings() {
    this.ngZone.run(() => {
      this.router.navigate(['/user/bookings']);
    });
  }
}
