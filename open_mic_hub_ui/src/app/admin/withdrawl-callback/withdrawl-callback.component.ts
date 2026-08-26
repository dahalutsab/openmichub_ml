import { Component, NgZone, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AdminService } from '../admin.service';
import { ToastService } from '../../auth/toastr.service';

@Component({
  selector: 'app-withdrawl-callback',
  standalone: false,
  templateUrl: './withdrawl-callback.component.html',
})
export class WithdrawlCallbackComponent implements OnInit {
  queryParams: any;

  constructor(
    private route: ActivatedRoute,
    private adminService: AdminService,
    private toast: ToastService,
    private router: Router,
    private ngZone: NgZone
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.queryParams = params;

      if (this.isComplete) {
        this.adminService.confirmPaymentViaPost(params).subscribe({
          next: () => {
            this.toast.showSuccess('Withdrawal processed successfully.');
            this.ngZone.run(() => {
              this.router.navigate(['/admin/transactions']);
            });
          },
          error: (err) => {
            console.error('Withdrawal Callback Failed', err);
            this.toast.showError('Withdrawal processing failed on the server.');
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
}
