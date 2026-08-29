import { NO_ERRORS_SCHEMA } from '@angular/core';
import { commonTestProviders } from '../../../testing/test-providers';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ListArtistComponent } from './list-artist.component';

describe('ListArtistComponent', () => {
  let component: ListArtistComponent;
  let fixture: ComponentFixture<ListArtistComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ListArtistComponent],
      providers: [...commonTestProviders],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ListArtistComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
