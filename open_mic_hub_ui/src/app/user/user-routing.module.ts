import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UserBaseComponent } from './components/user-base/user-base.component';
import { BOOKER_ROLES } from '../shared/role';
import { UserDashboardComponent } from './components/user-dashboard/user-dashboard.component';
import { authGuard } from '../auth/auth.guard';
import { ListArtistComponent } from './components/list-artist/list-artist.component';
import { MyBookingsComponent } from './components/my-bookings/my-bookings.component';
import { ViewArtistComponent } from './components/view-artist/view-artist.component';
import { PaymentHistoryComponent } from './components/payment-history/payment-history.component';
import { ArtistFeedComponent } from './components/artist-feed/artist-feed.component';
import { PaymentCallbackComponent } from './payment-callback/payment-callback.component';
import { BookingDetailsComponent } from './components/booking-details/booking-details.component';
import { ProfileComponent } from './components/profile/profile.component';

const routes: Routes = [
 {
    path: '',
    component: UserBaseComponent,
    canActivate: [authGuard],
    data: { roles: BOOKER_ROLES },
    
    
    children: [
      // Login lands on /user with no child path. Without this the shell
      // rendered around an empty router-outlet.
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'feeds',
        component: ArtistFeedComponent
      },
      {
        path: 'dashboard',
        component: UserDashboardComponent
      },
      {
         path: 'artists',
        component: ListArtistComponent
      },

        {
         path: 'view-artist/:id',
        component: ViewArtistComponent
      },
        {
         path: 'bookings',
        component: MyBookingsComponent
      },

      {
         path: 'book-details',
        component: BookingDetailsComponent
      },
       {
         path: 'artist-posts',
        component: ViewArtistComponent
      },

         {
         path: 'payment-history',
        component: PaymentHistoryComponent
      },
      
    { path: 'artist/payment-callback', component: PaymentCallbackComponent } ,

    {
  path: 'profile/:id',
  component: ProfileComponent 
}





]}
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class UserRoutingModule { }
