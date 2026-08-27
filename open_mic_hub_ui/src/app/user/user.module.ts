import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { UserRoutingModule } from './user-routing.module';
import { UserBaseComponent } from './components/user-base/user-base.component';
import { UserDashboardComponent } from './components/user-dashboard/user-dashboard.component';
import { HeaderComponent } from './components/header/header.component';
import { ProfileComponent } from './components/profile/profile.component';
import { SharedModule } from '../shared/shared.module';
import { ListArtistComponent } from './components/list-artist/list-artist.component';
import { MyBookingsComponent } from './components/my-bookings/my-bookings.component';
import { ViewArtistComponent } from './components/view-artist/view-artist.component';
import { PaymentHistoryComponent } from './components/payment-history/payment-history.component';
import { ArtistFeedComponent } from './components/artist-feed/artist-feed.component';
import { PaymentCallbackComponent } from './payment-callback/payment-callback.component';
import { BookingDetailsComponent } from './components/booking-details/booking-details.component';
import { OMH_CHARTS } from '../shared/charts';
import { OMH_REVIEWS } from '../shared/reviews';

@NgModule({
  declarations: [
    UserBaseComponent,
    UserDashboardComponent,
    HeaderComponent,
    ProfileComponent,
    ListArtistComponent,
    MyBookingsComponent,
    ViewArtistComponent,
    PaymentHistoryComponent,
    ArtistFeedComponent,
    PaymentCallbackComponent,
    BookingDetailsComponent
  ],
  imports: [
    CommonModule,
    UserRoutingModule,
    SharedModule,
    ReactiveFormsModule,
    FormsModule,
    ...OMH_CHARTS,
    ...OMH_REVIEWS,
  ]
})
export class UserModule { }
