import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { of, throwError } from 'rxjs';
import { AuthService } from '../auth.service';
import { CompleteProfileComponent } from './complete-profile.component';

/**
 * The one-time setup step after a first social sign-in.
 *
 * The submit guard carries the weight here: an artist without a stage name or a style produces an
 * account the rest of the platform cannot render, and the backend rejects it, so the button has to
 * stay closed until the answer is complete.
 */
describe('CompleteProfileComponent', () => {
  let fixture: ComponentFixture<CompleteProfileComponent>;
  let component: CompleteProfileComponent;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;
  let toast: jasmine.SpyObj<ToastrService>;

  const genres = [
    { id: 1, name: 'Jazz', categories: [{ id: 4, name: 'Bebop' }, { id: 5, name: 'Swing' }] },
    { id: 2, name: 'Rock', categories: [{ id: 9, name: 'Punk' }] },
  ];

  beforeEach(async () => {
    authService = jasmine.createSpyObj('AuthService',
      ['profileCompletionStatus', 'completeProfile', 'genres']);
    router = jasmine.createSpyObj('Router', ['navigate']);
    toast = jasmine.createSpyObj('ToastrService', ['error']);

    authService.profileCompletionStatus.and.returnValue(of({ data: { onboardingRequired: true } }));
    authService.genres.and.returnValue(of({ data: genres }));
    authService.completeProfile.and.returnValue(of({ data: { roles: ['ORGANIZER'] } }));

    await TestBed.configureTestingModule({
      declarations: [CompleteProfileComponent],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
        { provide: ToastrService, useValue: toast },
      ],
      schemas: [],
    }).overrideTemplate(CompleteProfileComponent, '').compileComponents();

    fixture = TestBed.createComponent(CompleteProfileComponent);
    component = fixture.componentInstance;
    component.ngOnInit();
    localStorage.clear();
  });

  afterEach(() => localStorage.clear());

  it('loads the genre list to choose from', () => {
    expect(component.genres.length).toBe(2);
  });

  it('sends an already-set-up account away rather than letting it answer twice', () => {
    authService.profileCompletionStatus.and.returnValue(of({ data: { onboardingRequired: false } }));

    component.ngOnInit();

    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  describe('the submit guard', () => {
    it('stays closed until a choice is made', () => {
      expect(component.canSubmit).toBeFalse();
    });

    it('opens as soon as an organizer chooses', () => {
      component.choose(false);
      expect(component.canSubmit).toBeTrue();
    });

    it('stays closed for an artist with no stage name', () => {
      component.choose(true);
      component.toggleSubGenre(4);
      expect(component.canSubmit).toBeFalse();
    });

    it('stays closed for an artist with no style chosen', () => {
      component.choose(true);
      component.stageName = 'Bibek Subedi';
      expect(component.canSubmit).toBeFalse();
    });

    it('opens once an artist has both', () => {
      component.choose(true);
      component.stageName = 'Bibek Subedi';
      component.toggleSubGenre(4);
      expect(component.canSubmit).toBeTrue();
    });

    it('stays closed while a save is in flight', () => {
      component.choose(false);
      component.isSaving = true;
      expect(component.canSubmit).toBeFalse();
    });
  });

  it('toggles a style off again', () => {
    component.toggleSubGenre(4);
    expect(component.selectedSubGenres.has(4)).toBeTrue();
    component.toggleSubGenre(4);
    expect(component.selectedSubGenres.has(4)).toBeFalse();
  });

  it('sends an organizer without any artist detail', () => {
    component.choose(false);
    component.location = 'Pokhara';

    component.submit();

    const payload = authService.completeProfile.calls.mostRecent().args[0];
    expect(payload.performing).toBeFalse();
    expect(payload.stageName).toBeNull();
    expect(payload.genres).toBeNull();
    expect(payload.location).toBe('Pokhara');
    expect(router.navigate).toHaveBeenCalledWith(['/user']);
  });

  it('groups chosen styles under their parent genre', () => {
    component.choose(true);
    component.stageName = '  Bibek Subedi  ';
    component.hourlyRate = 3500;
    component.toggleSubGenre(4);
    component.toggleSubGenre(9);
    authService.completeProfile.and.returnValue(of({ data: { roles: ['ARTIST'] } }));

    component.submit();

    const payload = authService.completeProfile.calls.mostRecent().args[0];
    expect(payload.stageName).toBe('Bibek Subedi');
    expect(payload.hourlyRate).toBe(3500);
    // Genres with nothing chosen are dropped rather than sent empty.
    expect(payload.genres).toEqual([
      { genreId: 1, subGenreIds: [4] },
      { genreId: 2, subGenreIds: [9] },
    ]);
    expect(router.navigate).toHaveBeenCalledWith(['/artist/']);
  });

  it('stores the roles the backend confirms, not the ones it asked for', () => {
    component.choose(true);
    component.stageName = 'Bibek Subedi';
    component.toggleSubGenre(4);
    authService.completeProfile.and.returnValue(of({ data: { roles: ['ARTIST'] } }));

    component.submit();

    expect(JSON.parse(localStorage.getItem('urole')!)).toEqual(['ARTIST']);
  });

  it('surfaces a rejection and lets the person try again', () => {
    component.choose(false);
    authService.completeProfile.and.returnValue(
      throwError(() => ({ error: { message: 'This account has already been set up.' } })));

    component.submit();

    expect(toast.error).toHaveBeenCalledWith('This account has already been set up.');
    expect(component.isSaving).toBeFalse();
  });
});
