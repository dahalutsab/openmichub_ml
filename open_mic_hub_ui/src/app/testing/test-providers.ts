import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { provideToastr } from 'ngx-toastr';

/**
 * What nearly every component in this application needs before it can be constructed at all.
 *
 * The generated specs supplied none of it, so all forty failed on `NullInjectorError` the first
 * time the suite was actually run — they had never passed. A smoke test that only proves a
 * component can be built is thin, but it is not worthless: it catches a constructor that asks for
 * something the module does not provide, which is a real and common way to break a lazy-loaded
 * route. Sharing the setup is what makes keeping them cheap.
 *
 * `provideHttpClientTesting` matters beyond wiring: it means a component that fires a request in
 * `ngOnInit` reaches a test backend rather than the network, so the suite neither hangs nor
 * depends on a running API.
 */
export const commonTestProviders = [
  provideHttpClient(),
  provideHttpClientTesting(),
  provideRouter([]),
  provideNoopAnimations(),
  provideToastr(),
  {
    // provideRouter alone does not give an injectable ActivatedRoute snapshot, and components
    // routinely read route params during construction.
    provide: ActivatedRoute,
    useValue: {
      snapshot: {
        paramMap: convertToParamMap({}),
        queryParamMap: convertToParamMap({}),
        params: {},
        queryParams: {},
        data: {},
      },
      paramMap: of(convertToParamMap({})),
      queryParamMap: of(convertToParamMap({})),
      params: of({}),
      queryParams: of({}),
      data: of({}),
    },
  },
];
