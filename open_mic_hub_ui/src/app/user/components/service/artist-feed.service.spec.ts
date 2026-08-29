import { commonTestProviders } from '../../../testing/test-providers';
import { TestBed } from '@angular/core/testing';

import { ArtistFeedService } from './artist-feed.service';

describe('ArtistFeedService', () => {
  let service: ArtistFeedService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...commonTestProviders] });
    service = TestBed.inject(ArtistFeedService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
