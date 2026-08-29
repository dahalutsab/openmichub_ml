import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LoginComponent } from './login/login.component';
import { RegisterComponent } from './register/register.component';
import { OtpComponent } from './otp/otp.component';
import { SocialComponent } from './social/social.component';

const routes: Routes = [
    {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full',
  },

  
  {
    path: 'login', component: LoginComponent
  },

   {
    path: 'register', component: RegisterComponent
  },
  
{ path: 'verify-email', component: OtpComponent },

  // Landing point for Google and Facebook sign-in; the backend redirects here.
  { path: 'social', component: SocialComponent }

 


];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class AuthRoutingModule { }
