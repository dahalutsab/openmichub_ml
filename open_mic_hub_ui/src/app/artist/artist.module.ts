import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { ArtistRoutingModule } from './artist-routing.module';
import { ArtistBaseComponent } from './components/artist-base/artist-base.component';
import { ArtistDashboardComponent } from './components/artist-dashboard/artist-dashboard.component';
import { ProfileCompletenessComponent } from './components/profile-completeness/profile-completeness.component';
import { ProfileComponent } from './components/profile/profile.component';
import { HeaderComponent } from './components/header/header.component';
import { ArtistCalendarComponent } from './components/artist-calendar/artist-calendar.component';
import { SharedModule } from '../shared/shared.module';
import { CreatePostComponent } from './components/post-management/create-post/create-post.component';
import { ListPostComponent } from './components/post-management/list-post/list-post.component';
import { ViewPostComponent } from './components/post-management/view-post/view-post.component';
import { EditPostComponent } from './components/post-management/edit-post/edit-post.component';
import { BookingsComponent } from './components/bookings/bookings.component';
import { CoinTransactionComponent } from './components/coin-transaction/coin-transaction.component';
import { PaymentRecordComponent } from './components/payment-record/payment-record.component';
import { VirtualMoneyComponent } from './components/virtual-money/virtual-money.component';
import { OMH_CHARTS } from '../shared/charts';
import { OMH_REVIEWS } from '../shared/reviews';
import { MediaUrlPipe } from '../shared/media-url.pipe';

/**
 * Two things used to be imported here that should not have been:
 *
 * - `ToastrModule.forRoot()`. This is a lazy-loaded module, so calling forRoot
 *   again built a second ToastrService in the child injector, separate from the
 *   one AppModule configures. Toasts raised here used default settings rather
 *   than the app's, and the two instances could each own a container.
 * - `HttpClientModule`, already provided once at the root.
 *
 * Angular Material went the same way as in AdminModule: imported in bulk, never
 * rendered, and never themed.
 */
@NgModule({
  declarations: [
    ArtistBaseComponent,
    ArtistDashboardComponent,
    ProfileCompletenessComponent,
    ProfileComponent,
    HeaderComponent,
    ArtistCalendarComponent,
    CreatePostComponent,
    ListPostComponent,
    ViewPostComponent,
    EditPostComponent,
    BookingsComponent,
    CoinTransactionComponent,
    PaymentRecordComponent,
    VirtualMoneyComponent
  ],
  imports: [
    CommonModule,
    ArtistRoutingModule,
    SharedModule,
    ReactiveFormsModule,
    FormsModule,
    ...OMH_CHARTS,
    ...OMH_REVIEWS,
   MediaUrlPipe]
})
export class ArtistModule { }
