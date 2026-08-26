import { ComponentFixture, TestBed } from '@angular/core/testing';

import { VirtualMoneyComponent } from './virtual-money.component';

describe('VirtualMoneyComponent', () => {
  let component: VirtualMoneyComponent;
  let fixture: ComponentFixture<VirtualMoneyComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [VirtualMoneyComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(VirtualMoneyComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
