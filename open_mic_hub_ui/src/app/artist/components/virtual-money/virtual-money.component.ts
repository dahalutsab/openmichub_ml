import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AbstractControl, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { environment } from '../../../environment/environment';

interface VirtualCoinResponse {
  timestamp: string;
  message: string;
  data: {
    virtualCoinId: number;
    balance: number;
    artist: {
      fullName: string;
      emailId: string;
      profileImage: string;
      roles: any[];
      stageName: string;
      bio: string;
      active: boolean;
      verified: boolean;
    };
  };
  status: string;
}

interface WithdrawResponse {
  timestamp: string;
  message: string;
  data: {
    transactionId: number;
    artistName: string;
    amount: number;
    transactionType: string;
    transactionPurpose: string;
    transactionStatus: string;
  };
  status: string;
}

/** Inline SVG stand-in, used when an artist's picture fails to load. */
const AVATAR_FALLBACK =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 80 80">
       <rect width="80" height="80" fill="#eeedf3"/>
       <circle cx="40" cy="32" r="13" fill="#b6b2c6"/>
       <path d="M14 74c0-14.4 11.6-24 26-24s26 9.6 26 24z" fill="#b6b2c6"/>
     </svg>`
  );

@Component({
  selector: 'app-virtual-money',
  standalone: false,
  templateUrl: './virtual-money.component.html',
})
export class VirtualMoneyComponent implements OnInit {
  virtualCoinData: VirtualCoinResponse | null = null;
  withdrawForm: FormGroup;
  withdrawResponse: WithdrawResponse | null = null;
  loading = true;
  withdrawProcessing = false;
  errorMessage = '';

  // Modal state. These were Bootstrap modals driven from TypeScript via
  // `new window.bootstrap.Modal(...)`; the bundle that provided that is gone,
  // so they are plain component state now.
  withdrawOpen = false;
  successOpen = false;

  private readonly apiBaseUrl = environment.baseUrl;

  constructor(
    private http: HttpClient,
    private fb: FormBuilder
  ) {
    this.withdrawForm = this.fb.group({
      amount: ['', [
        Validators.required,
        Validators.min(1),
        this.maxBalanceValidator.bind(this)
      ]]
    });
  }

  ngOnInit() {
    this.loadWalletData();
  }

  /** Flattened view of the response, so the template is not five levels deep. */
  get artist() {
    const data = this.virtualCoinData?.data;
    return {
      walletId: data?.virtualCoinId ?? 0,
      balance: data?.balance ?? 0,
      fullName: data?.artist?.fullName ?? 'Artist',
      stageName: data?.artist?.stageName ?? '',
      verified: data?.artist?.verified ?? false,
      active: data?.artist?.active ?? false,
      profileImage: data?.artist?.profileImage ?? '',
    };
  }

  get amountControl(): AbstractControl | null {
    return this.withdrawForm.get('amount');
  }

  loadWalletData() {
    this.loading = true;
    this.errorMessage = '';

    this.http.get<VirtualCoinResponse>(`${this.apiBaseUrl}/virtual-coins/get-logged-in-artist`)
      .subscribe({
        next: (response) => {
          this.virtualCoinData = response;
          this.loading = false;
          // Re-run the max validator against the balance that just arrived.
          this.amountControl?.updateValueAndValidity();
        },
        error: (error) => {
          console.error('Error loading wallet data:', error);
          this.errorMessage = 'Failed to load wallet data. Please try again.';
          this.loading = false;
        }
      });
  }

  maxBalanceValidator(control: AbstractControl) {
    if (!this.virtualCoinData) return null;
    const value = control.value;
    const balance = this.virtualCoinData.data.balance;
    if (value && value > balance) {
      return { max: { max: balance, actual: value } };
    }
    return null;
  }

  getProfileImageUrl(): string {
    return this.artist.profileImage || AVATAR_FALLBACK;
  }

  onImageError(event: any) {
    event.target.src = AVATAR_FALLBACK;
  }

  openWithdraw() {
    if (this.artist.balance > 0) {
      this.withdrawForm.reset();
      this.withdrawOpen = true;
    }
  }

  closeWithdraw() {
    this.withdrawOpen = false;
  }

  closeSuccess() {
    this.successOpen = false;
    this.loadWalletData();
  }

  submitWithdraw() {
    if (this.withdrawForm.invalid || this.withdrawProcessing) {
      this.withdrawForm.markAllAsTouched();
      return;
    }

    this.withdrawProcessing = true;
    this.errorMessage = '';

    const amount = this.withdrawForm.value.amount;

    this.http.post<WithdrawResponse>(`${this.apiBaseUrl}/transactions/withDraw`, { amount })
      .subscribe({
        next: (response) => {
          this.withdrawResponse = response;
          this.withdrawProcessing = false;
          this.withdrawOpen = false;
          this.successOpen = true;
        },
        error: (error) => {
          console.error('Error submitting withdrawal:', error);
          this.errorMessage = error.error?.message || 'Failed to submit withdrawal request. Please try again.';
          this.withdrawProcessing = false;
          this.withdrawOpen = false;
        }
      });
  }
}
