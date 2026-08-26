import { Component, NgZone, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { UserService } from '../user.service';
import { MatSnackBar } from '@angular/material/snack-bar'; 

@Component({
  selector: 'app-payment-callback',
  standalone:false,
  templateUrl: './payment-callback.component.html',
  styleUrls: ['./payment-callback.component.scss']
})
export class PaymentCallbackComponent implements OnInit {
  queryParams: any;

  constructor(
    private route: ActivatedRoute,
    private userService: UserService,
    private snackBar: MatSnackBar,         
    private router: Router  ,
      private ngZone: NgZone 
  ) {}

  ngOnInit(): void {
  this.route.queryParams.subscribe(params => {
    this.queryParams = params;

    console.log('Callback Params:', params); // Debug

    if (params['status']?.toLowerCase() === 'completed') {
      this.userService.confirmPaymentViaPost(params).subscribe({
        next: (res) => {
          this.snackBar.open('Payment Successful!', 'Close', { duration: 3000 });

          this.ngZone.run(() => {
            this.router.navigate(['/user/bookings']);
          });
        },
        error: (err) => {
          console.error('Payment Callback Failed', err);
          this.snackBar.open('Payment processing failed on server.', 'Close', { duration: 3000 });
        }
      });
    }
  });
}


goToBookings() {
  this.ngZone.run(() => {
    this.router.navigate(['/user/bookings']);
  });
}

}
