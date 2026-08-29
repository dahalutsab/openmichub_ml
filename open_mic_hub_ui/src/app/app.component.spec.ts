import { NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { AppComponent } from './app.component';
import { commonTestProviders } from './testing/test-providers';

/**
 * The root shell.
 *
 * This was CLI boilerplate asserting a `title` property and an `h1` greeting, neither of which
 * this component has ever had. It did not merely fail — it failed to compile, and because Karma
 * type-checks every spec before running any of them, one stale file kept the entire suite from
 * running at all.
 */
describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RouterModule.forRoot([])],
      declarations: [AppComponent],
      providers: [...commonTestProviders],
      // The shell hosts feature components declared in lazy modules — app-chatbot among them —
      // which are not loaded here and do not need to be for this to mean anything.
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  });

  it('creates the root shell', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders a router outlet for the feature modules to fill', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).not.toBeNull();
  });
});
