import {Component, NgZone, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {UserService} from '../../user/user.service';
import {MatSnackBar} from '@angular/material/snack-bar';
import {AdminService} from '../admin.service';

@Component({
  selector: 'app-withdrawl-callback',
  standalone: false,
  templateUrl: './withdrawl-callback.component.html',
  styleUrl: './withdrawl-callback.component.scss'
})
export class WithdrawlCallbackComponent implements OnInit {
  queryParams: any;

  constructor(
    private route: ActivatedRoute,
    private adminService: AdminService,
    private snackBar: MatSnackBar,
    private router: Router  ,
    private ngZone: NgZone
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.queryParams = params;

      console.log('Callback Params:', params); // Debug

      if (params['status']?.toLowerCase() === 'completed') {
        this.adminService.confirmPaymentViaPost(params).subscribe({
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
