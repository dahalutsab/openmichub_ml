import { commonTestProviders } from '../testing/test-providers';
import { TestBed } from '@angular/core/testing';
import { CanActivateFn } from '@angular/router';

import { authGuard } from './auth.guard';

describe('authGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) => 
      TestBed.runInInjectionContext(() => authGuard(...guardParameters));

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...commonTestProviders] });
  });

  it('should be created', () => {
    expect(executeGuard).toBeTruthy();
  });
});
