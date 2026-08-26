import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ArtistBaseComponent } from './artist-base.component';

describe('ArtistBaseComponent', () => {
  let component: ArtistBaseComponent;
  let fixture: ComponentFixture<ArtistBaseComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ArtistBaseComponent]
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
