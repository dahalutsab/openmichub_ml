import { NO_ERRORS_SCHEMA } from '@angular/core';
import { commonTestProviders } from '../../../testing/test-providers';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserBaseComponent } from './user-base.component';

describe('UserBaseComponent', () => {
  let component: UserBaseComponent;
  let fixture: ComponentFixture<UserBaseComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [UserBaseComponent],
      providers: [...commonTestProviders],
      schemas: [NO_ERRORS_SCHEMA]
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
