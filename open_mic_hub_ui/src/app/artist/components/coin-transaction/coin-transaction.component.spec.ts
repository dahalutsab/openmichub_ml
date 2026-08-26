import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CoinTransactionComponent } from './coin-transaction.component';

describe('CoinTransactionComponent', () => {
  let component: CoinTransactionComponent;
  let fixture: ComponentFixture<CoinTransactionComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [CoinTransactionComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CoinTransactionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
