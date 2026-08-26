import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ArtistDashboardComponent } from './artist-dashboard.component';

describe('ArtistDashboardComponent', () => {
  let component: ArtistDashboardComponent;
  let fixture: ComponentFixture<ArtistDashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ArtistDashboardComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ArtistDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
