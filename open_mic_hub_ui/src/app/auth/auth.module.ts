import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthRoutingModule } from './auth-routing.module';
import { LoginComponent } from './login/login.component';
import { RegisterComponent } from './register/register.component';
import { ReactiveFormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { OtpComponent } from './otp/otp.component';
import { SocialComponent } from './social/social.component';


@NgModule({
  declarations: [
    LoginComponent,
    RegisterComponent,
    OtpComponent,
    SocialComponent,

  ],
  imports: [
    CommonModule,
    AuthRoutingModule,
    ReactiveFormsModule,
    HttpClientModule,
    
  ]
})
export class AuthModule { }
