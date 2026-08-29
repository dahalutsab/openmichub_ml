import { commonTestProviders } from './testing/test-providers';
import { TestBed } from '@angular/core/testing';

import { LandingService } from './landing.service';

describe('LandingService', () => {
  let service: LandingService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...commonTestProviders] });
    service = TestBed.inject(LandingService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
