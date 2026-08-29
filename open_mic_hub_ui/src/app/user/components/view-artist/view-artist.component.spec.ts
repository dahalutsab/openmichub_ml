import { NO_ERRORS_SCHEMA } from '@angular/core';
import { commonTestProviders } from '../../../testing/test-providers';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewArtistComponent } from './view-artist.component';

describe('ViewArtistComponent', () => {
  let component: ViewArtistComponent;
  let fixture: ComponentFixture<ViewArtistComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      // Standalone, so it is imported rather than declared.
      imports: [ViewArtistComponent],
      providers: [...commonTestProviders],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ViewArtistComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
