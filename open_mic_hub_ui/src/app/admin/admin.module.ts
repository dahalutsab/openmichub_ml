import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { AdminRoutingModule } from './admin-routing.module';
import { AdminBaseComponent } from './components/admin-base/admin-base.component';
import { AdminDashboardComponent } from './components/admin-dashboard/admin-dashboard.component';
import { HeaderComponent } from './components/header/header.component';
import { ProfileComponent } from './components/profile/profile.component';
import { SharedModule } from '../shared/shared.module';
import { UsersListComponent } from './components/users-list/users-list.component';
import { TransactionsListComponent } from './components/coin-transaction/transactions-list.component';
import { PaymentRecordsComponent } from './components/payment-records/payment-records.component';
import {FormsModule} from "@angular/forms";
import { WithdrawlCallbackComponent } from './withdrawl-callback/withdrawl-callback.component';
import {MatButton, MatButtonModule} from '@angular/material/button';
import {
  MatCard,
  MatCardActions,
  MatCardAvatar,
  MatCardContent,
  MatCardHeader, MatCardModule,
  MatCardSubtitle, MatCardTitle
} from '@angular/material/card';
import {MatTableModule} from '@angular/material/table';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatListModule} from '@angular/material/list';
import {MatIcon, MatIconModule} from '@angular/material/icon';
import {MatSnackBarModule} from '@angular/material/snack-bar';


@NgModule({
  declarations: [
    AdminBaseComponent,
    AdminDashboardComponent,
    HeaderComponent,
    ProfileComponent,
    UsersListComponent,
    TransactionsListComponent,
    PaymentRecordsComponent,
    WithdrawlCallbackComponent
  ],
  imports: [
    CommonModule,
    AdminRoutingModule,
    SharedModule,
    FormsModule,
    MatButton,
    MatCard,
    MatCardActions,
    MatCardAvatar,
    MatCardContent,
    MatCardHeader,
    MatCardSubtitle,
    MatCardTitle,
    MatTableModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatListModule,
    MatIconModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatSnackBarModule,
    MatIcon
  ]
})
export class AdminModule { }
