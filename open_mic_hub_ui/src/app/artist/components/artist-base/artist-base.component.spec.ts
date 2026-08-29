import { NO_ERRORS_SCHEMA } from '@angular/core';
import { commonTestProviders } from '../../../testing/test-providers';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ArtistBaseComponent } from './artist-base.component';

describe('ArtistBaseComponent', () => {
  let component: ArtistBaseComponent;
  let fixture: ComponentFixture<ArtistBaseComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ArtistBaseComponent],
      providers: [...commonTestProviders],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ArtistBaseComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
