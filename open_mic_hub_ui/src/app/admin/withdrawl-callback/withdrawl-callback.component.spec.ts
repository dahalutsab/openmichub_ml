import { ComponentFixture, TestBed } from '@angular/core/testing';

import { WithdrawlCallbackComponent } from './withdrawl-callback.component';

describe('WithdrawlCallbackComponent', () => {
  let component: WithdrawlCallbackComponent;
  let fixture: ComponentFixture<WithdrawlCallbackComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [WithdrawlCallbackComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(WithdrawlCallbackComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
