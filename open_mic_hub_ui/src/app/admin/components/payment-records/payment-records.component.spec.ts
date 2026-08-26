import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PaymentRecordsComponent } from './payment-records.component';

describe('PaymentRecordsComponent', () => {
  let component: PaymentRecordsComponent;
  let fixture: ComponentFixture<PaymentRecordsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [PaymentRecordsComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PaymentRecordsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
