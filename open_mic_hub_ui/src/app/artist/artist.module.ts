import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtistRoutingModule } from './artist-routing.module';
import { ArtistBaseComponent } from './components/artist-base/artist-base.component';
import { ArtistDashboardComponent } from './components/artist-dashboard/artist-dashboard.component';
import { ProfileComponent } from './components/profile/profile.component';
import { HeaderComponent } from './components/header/header.component';
import { ArtistCalendarComponent } from './components/artist-calendar/artist-calendar.component';
import { SharedModule } from '../shared/shared.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ToastrModule } from 'ngx-toastr';
import { HttpClientModule } from '@angular/common/http';
import { CreatePostComponent } from './components/post-management/create-post/create-post.component';
import { ListPostComponent } from './components/post-management/list-post/list-post.component';
import { ViewPostComponent } from './components/post-management/view-post/view-post.component';
import { EditPostComponent } from './components/post-management/edit-post/edit-post.component';
import { BookingsComponent } from './components/bookings/bookings.component';
import { CoinTransactionComponent } from './components/coin-transaction/coin-transaction.component';
import { PaymentRecordComponent } from './components/payment-record/payment-record.component';
import { VirtualMoneyComponent } from './components/virtual-money/virtual-money.component';

@NgModule({
  declarations: [
    ArtistBaseComponent,
    ArtistDashboardComponent,
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
    MatCardModule,
    MatTabsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    ToastrModule.forRoot(),
    HttpClientModule
  ]
})
export class ArtistModule { }