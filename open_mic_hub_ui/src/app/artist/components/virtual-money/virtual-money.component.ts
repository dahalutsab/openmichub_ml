import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

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

@Component({
  selector: 'app-virtual-money',
  standalone: false,
  templateUrl: './virtual-money.component.html',
  styleUrls: ['./virtual-money.component.scss'],
})
export class VirtualMoneyComponent implements OnInit {
  virtualCoinData: VirtualCoinResponse | null = null;
  withdrawForm: FormGroup;
  withdrawResponse: WithdrawResponse | null = null;
  loading = true;
  withdrawProcessing = false;
  errorMessage = '';

  private readonly API_BASE_URL = 'http://localhost:8181/api/v1';

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

  loadWalletData() {
    this.loading = true;
    this.errorMessage = '';

    this.http.get<VirtualCoinResponse>(`${this.API_BASE_URL}/virtual-coins/get-logged-in-artist`)
      .subscribe({
        next: (response) => {
          this.virtualCoinData = response;
          this.loading = false;
          // Update form validators with new balance
          this.withdrawForm.get('amount')?.updateValueAndValidity();
        },
        error: (error) => {
          console.error('Error loading wallet data:', error);
          this.errorMessage = 'Failed to load wallet data. Please try again.';
          this.loading = false;
        }
      });
  }

  maxBalanceValidator(control: any) {
    if (!this.virtualCoinData) return null;
    const value = control.value;
    if (value && value > this.virtualCoinData.data.balance) {
      return { max: { max: this.virtualCoinData.data.balance, actual: value } };
    }
    return null;
  }

  getProfileImageUrl(): string {
    if (this.virtualCoinData?.data.artist.profileImage) {
      return this.virtualCoinData.data.artist.profileImage;
    }
    return 'assets/default-avatar.png'; // Fallback image
  }

  onImageError(event: any) {
    event.target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iODAiIGhlaWdodD0iODAiIHZpZXdCb3g9IjAgMCA4MCA4MCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPGNpcmNsZSBjeD0iNDAiIGN5PSI0MCIgcj0iNDAiIGZpbGw9IiNFNUU3RUIiLz4KPHN2ZyB4PSIyMCIgeT0iMjAiIHdpZHRoPSI0MCIgaGVpZ2h0PSI0MCIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSIjOUM5Q0EzIj4KPHA+PGNpcmNsZSBjeD0iMTIiIGN5PSI4IiByPSIzIi8+CjxwYXRoIGQ9Im0xMiAxNGMtNC40IDAtOCAyLjctOCA2djJoMTZ2LTJjMC0zLjMtMy42LTYtOC02eiIvPgo8L3N2Zz4KPC9zdmc+';
  }

  showWithdrawModal() {
    if (this.virtualCoinData && this.virtualCoinData.data.balance > 0) {
      this.withdrawForm.reset();
      // Use Bootstrap's modal method
      const modal = new (window as any).bootstrap.Modal(document.getElementById('withdrawModal'));
      modal.show();
    }
  }

  submitWithdraw() {
    if (this.withdrawForm.valid && !this.withdrawProcessing) {
      this.withdrawProcessing = true;
      this.errorMessage = '';

      const amount = this.withdrawForm.value.amount;

      this.http.post<WithdrawResponse>(`${this.API_BASE_URL}/transactions/withDraw`, { amount })
        .subscribe({
          next: (response) => {
            this.withdrawResponse = response;
            this.withdrawProcessing = false;

            // Hide withdraw modal
            const withdrawModal = (window as any).bootstrap.Modal.getInstance(document.getElementById('withdrawModal'));
            withdrawModal.hide();

            // Show success modal
            const successModal = new (window as any).bootstrap.Modal(document.getElementById('successModal'));
            successModal.show();
          },
          error: (error) => {
            console.error('Error submitting withdrawal:', error);
            this.errorMessage = error.error?.message || 'Failed to submit withdrawal request. Please try again.';
            this.withdrawProcessing = false;
          }
        });
    }
  }
}
