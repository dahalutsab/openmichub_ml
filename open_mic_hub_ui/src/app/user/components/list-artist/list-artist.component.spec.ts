import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ListArtistComponent } from './list-artist.component';

describe('ListArtistComponent', () => {
  let component: ListArtistComponent;
  let fixture: ComponentFixture<ListArtistComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ListArtistComponent]
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
