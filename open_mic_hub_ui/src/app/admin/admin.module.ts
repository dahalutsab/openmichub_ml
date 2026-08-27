import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { AdminRoutingModule } from './admin-routing.module';
import { AdminBaseComponent } from './components/admin-base/admin-base.component';
import { AdminDashboardComponent } from './components/admin-dashboard/admin-dashboard.component';
import { HeaderComponent } from './components/header/header.component';
import { ProfileComponent } from './components/profile/profile.component';
import { SharedModule } from '../shared/shared.module';
import { UsersListComponent } from './components/users-list/users-list.component';
import { TransactionsListComponent } from './components/coin-transaction/transactions-list.component';
import { PaymentRecordsComponent } from './components/payment-records/payment-records.component';
import { WithdrawlCallbackComponent } from './withdrawl-callback/withdrawl-callback.component';
import { OMH_CHARTS } from '../shared/charts';
import { MediaUrlPipe } from '../shared/media-url.pipe';

/**
 * Angular Material used to be imported here in bulk. No template in the module
 * rendered a `mat-*` element, and no Material theme was ever loaded, so the two
 * screens that did use it rendered unstyled. Both are rewritten; the imports
 * are gone with them.
 */
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
    ...OMH_CHARTS,
   MediaUrlPipe]
})
export class AdminModule { }
