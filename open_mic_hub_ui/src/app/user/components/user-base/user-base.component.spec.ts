import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserBaseComponent } from './user-base.component';

describe('UserBaseComponent', () => {
  let component: UserBaseComponent;
  let fixture: ComponentFixture<UserBaseComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [UserBaseComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(UserBaseComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
