import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LandingPageComponent } from './landing-page/landing-page.component';
import {ChatComponent} from './messaging/component/chat.component';
import { PaymentCallbackComponent } from './user/payment-callback/payment-callback.component';
// import {ChatComponent} from './messaging/chat.component';

const routes: Routes = [
   { path: '', component: LandingPageComponent },

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
