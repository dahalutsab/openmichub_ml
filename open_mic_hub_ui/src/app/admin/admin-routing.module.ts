import { Component, NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AdminBaseComponent } from './components/admin-base/admin-base.component';
import { AdminDashboardComponent } from './components/admin-dashboard/admin-dashboard.component';
import { authGuard } from '../auth/auth.guard';
import { ADMIN_ROLES } from '../shared/role';
import {UsersListComponent} from './components/users-list/users-list.component';
import {TransactionsListComponent} from './components/coin-transaction/transactions-list.component';
import {PaymentRecordsComponent} from './components/payment-records/payment-records.component';
import { ProfileComponent } from './components/profile/profile.component';
import {WithdrawlCallbackComponent} from './withdrawl-callback/withdrawl-callback.component';

const routes: Routes = [
 {
    path: '',
    component: AdminBaseComponent,
    canActivate: [authGuard],
    data: { roles: ADMIN_ROLES },
    children: [
      // Login lands on /admin or /artist with no child path. Without this
      // the shell rendered around an empty router-outlet.
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        component: AdminDashboardComponent
      },
      {
        path: 'users',
        component: UsersListComponent
      },
      {
        path: 'transactions',
        component: TransactionsListComponent
      },
      {
        path: 'payments',
        component: PaymentRecordsComponent
      },
      {
        path: 'profile',
        component: ProfileComponent
      },
      {
        path: 'withdraw/callback',
        component: WithdrawlCallbackComponent
      }
]}
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class AdminRoutingModule { }
