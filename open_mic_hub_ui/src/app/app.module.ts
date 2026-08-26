import { NgModule } from '@angular/core';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { BrowserModule } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
import {FormsModule} from "@angular/forms";
import { LandingPageComponent } from './landing-page/landing-page.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { AuthInterceptor } from './auth/auth-interceptor';
import { SidebarComponent } from './shared/sidebar/sidebar.component';
import { ToastrModule } from 'ngx-toastr';
import {RxStompService, rxStompServiceFactory} from '@stomp/ng2-stompjs';
import {stompConfig} from './stomp/stomp.config';
import {ChatBotComponent} from "./chat_bot/component/chat-bot.component";

@NgModule({
  declarations: [
    AppComponent,
    LandingPageComponent,

  ],
  imports: [
    CommonModule,
    BrowserModule,
    BrowserAnimationsModule,
    AppRoutingModule,
    HttpClientModule,
    FormsModule,
    ToastrModule.forRoot({
      preventDuplicates: true,
      positionClass: 'toast-bottom-right'
    }),
    ChatBotComponent,


  ],
  providers: [  { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
    {
      provide: RxStompService,
      useFactory: () => rxStompServiceFactory(stompConfig)
    }],

  bootstrap: [AppComponent]
})
export class AppModule { }
