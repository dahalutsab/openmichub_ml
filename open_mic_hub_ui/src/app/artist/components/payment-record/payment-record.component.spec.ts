import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PaymentRecordComponent } from './payment-record.component';

describe('PaymentRecordComponent', () => {
  let component: PaymentRecordComponent;
  let fixture: ComponentFixture<PaymentRecordComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [PaymentRecordComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PaymentRecordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
