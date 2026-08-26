import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ArtistBaseComponent } from './components/artist-base/artist-base.component';
import { authGuard } from '../auth/auth.guard';
import { Role } from '../shared/role';
import { ArtistDashboardComponent } from './components/artist-dashboard/artist-dashboard.component';
import { ArtistCalendarComponent } from './components/artist-calendar/artist-calendar.component';
import { ListArtistComponent } from '../user/components/list-artist/list-artist.component';
import { ListPostComponent } from './components/post-management/list-post/list-post.component';
import { CreatePostComponent } from './components/post-management/create-post/create-post.component';
import { EditPostComponent } from './components/post-management/edit-post/edit-post.component';
import { ViewPostComponent } from './components/post-management/view-post/view-post.component';
import { BookingsComponent } from './components/bookings/bookings.component';
import { CoinTransactionComponent } from './components/coin-transaction/coin-transaction.component';
import { PaymentRecordComponent } from './components/payment-record/payment-record.component';
import { ProfileComponent } from './components/profile/profile.component';
import {VirtualMoneyComponent} from './components/virtual-money/virtual-money.component';

const routes: Routes = [
  {
    path: '',
    component: ArtistBaseComponent,
    canActivate: [authGuard],
    data: { roles: [Role.ARTIST] },
    children: [
      {
        path: 'dashboard',
        component: ArtistDashboardComponent
      },
      {
        path: 'calender',
        component: ArtistCalendarComponent
      },
         {
            path: 'bookings',
            component: BookingsComponent
          },
      {
        path: 'posts',
        children: [
          {
            path: '',
            component: ListPostComponent
          },
          {
            path: 'create',
            component: CreatePostComponent
          },
          {
            path: 'update/:id',
            component: EditPostComponent
          },
          {
            path: 'view/:id',
            component: ViewPostComponent
          }
        ]
      },

      {
        path: 'my-bookings',
        component: BookingsComponent
      },

      {
        path: 'wallet',
        component: VirtualMoneyComponent
      },

      {
        path: 'coin-transactions',
        component: CoinTransactionComponent
      },

      {
        path: 'payment-records',
        component: PaymentRecordComponent
      },

      {
        path: 'profile',
        component: ProfileComponent
      },

    ]
  }
];

@NgModule({
      imports: [RouterModule.forChild(routes)],
      exports: [RouterModule]
    })
  export class ArtistRoutingModule { }
