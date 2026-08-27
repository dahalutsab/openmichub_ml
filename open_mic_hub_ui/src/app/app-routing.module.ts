import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LandingPageComponent } from './landing-page/landing-page.component';
import {ChatComponent} from './messaging/component/chat.component';
import { PaymentCallbackComponent } from './user/payment-callback/payment-callback.component';
// import {ChatComponent} from './messaging/chat.component';

const routes: Routes = [
   { path: '', component: LandingPageComponent },

  /**
   * Artist profiles are public.
   *
   * Browsing is what brings organizers to the platform, and discovery — itself
   * public — links straight here. Behind the booker guard, a visitor following
   * one of those links hit the login screen before seeing anything. Booking
   * still requires an account; the profile checks that when the button is used.
   */
  {
    path: 'artists/:id',
    loadComponent: () =>
      import('./user/components/view-artist/view-artist.component')
        .then(m => m.ViewArtistComponent),
  },

    {
    path: 'user/payment-callback',
    component: PaymentCallbackComponent
  },


  {
    path: 'auth',
    loadChildren: () => import('./auth/auth.module').then(m => m.AuthModule)

  },
  {
    path:'admin',
    loadChildren: () => import ('./admin/admin.module'). then(m => m.AdminModule)
  },

   {
    path: 'user',
    loadChildren: () => import('./user/user.module').then(m => m.UserModule)
  },

  {
    path:'artist',
    loadChildren: () => import ('./artist/artist.module'). then(m => m.ArtistModule)
  },

  {
    path: 'discover',
    loadChildren: () => import('./discovery/discovery.routes').then(m => m.DISCOVERY_ROUTES)
  },

  {
    path: 'chat',
    component: ChatComponent
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
