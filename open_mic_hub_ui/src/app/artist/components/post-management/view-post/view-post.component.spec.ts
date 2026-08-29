import { NO_ERRORS_SCHEMA } from '@angular/core';
import { commonTestProviders } from '../../../../testing/test-providers';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewPostComponent } from './view-post.component';

describe('ViewPostComponent', () => {
  let component: ViewPostComponent;
  let fixture: ComponentFixture<ViewPostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ViewPostComponent],
      providers: [...commonTestProviders],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ViewPostComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
