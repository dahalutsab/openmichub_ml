import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { commonTestProviders } from '../../testing/test-providers';
import { RegisterComponent } from './register.component';

/**
 * Which tab registration opens on.
 *
 * "List your act" on the landing page is someone saying they are here to perform. That intent was
 * dropped at the door — the button navigated to a bare /auth/register, which defaults to booking —
 * so a performer had to notice a tab and switch it before the form asked for a stage name, a bio
 * or any genres. Switching the tab also swaps the validators, so opening on the wrong one is not
 * only confusing, it means the form is validating for the wrong thing.
 */
describe('RegisterComponent opening tab', () => {
  let fixture: ComponentFixture<RegisterComponent>;
  let component: RegisterComponent;

  async function createWith(queryParams: Record<string, string>) {
    await TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      declarations: [RegisterComponent],
      imports: [ReactiveFormsModule],
      providers: [
        ...commonTestProviders,
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap(queryParams) },
            queryParamMap: of(convertToParamMap(queryParams)),
            params: of({}),
          },
        },
      ],
    }).overrideTemplate(RegisterComponent, '').compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    component.ngOnInit();
  }

  it('opens on the performing tab when asked to', async () => {
    await createWith({ as: 'artist' });

    expect(component.activeRole).toBe('artist');
    expect(component.registerForm.get('role')?.value).toBe('artist');
  });

  it('requires what a performer profile needs', async () => {
    await createWith({ as: 'artist' });

    // Opening on the right tab is what puts these validators in place.
    expect(component.registerForm.get('stageName')?.hasError('required')).toBeTrue();
    expect(component.registerForm.get('bio')?.hasError('required')).toBeTrue();
    expect(component.registerForm.get('genres')?.hasError('required')).toBeTrue();
  });

  it('opens on booking by default', async () => {
    await createWith({});

    expect(component.activeRole).toBe('user');
    expect(component.registerForm.get('stageName')?.hasError('required')).toBeFalsy();
  });

  it('ignores a value it does not recognise rather than opening on it', async () => {
    await createWith({ as: 'something-else' });

    expect(component.activeRole).toBe('user');
  });
});
