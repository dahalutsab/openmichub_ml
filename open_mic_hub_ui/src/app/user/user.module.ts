import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { UserRoutingModule } from './user-routing.module';
import { UserBaseComponent } from './components/user-base/user-base.component';
import { UserDashboardComponent } from './components/user-dashboard/user-dashboard.component';
import { HeaderComponent } from './components/header/header.component';
import { ProfileComponent } from './components/profile/profile.component';
import { SharedModule } from '../shared/shared.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ListArtistComponent } from './components/list-artist/list-artist.component';
import { MyBookingsComponent } from './components/my-bookings/my-bookings.component';
import { ViewArtistComponent } from './components/view-artist/view-artist.component';
import { PaymentHistoryComponent } from './components/payment-history/payment-history.component';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { ArtistFeedComponent } from './components/artist-feed/artist-feed.component';
import { PaymentCallbackComponent } from './payment-callback/payment-callback.component';
import { MatCardModule } from '@angular/material/card';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { BookingDetailsComponent } from './components/booking-details/booking-details.component';


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
    MatTableModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatListModule,
    MatIconModule,
       MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatSnackBarModule,
  ]
})
export class UserModule { }
