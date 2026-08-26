import {
  Directive,
  ElementRef,
  EventEmitter,
  HostListener,
  Output,
} from '@angular/core';

/**
 * Emits when a click lands outside the host element.
 *
 * Dropdowns used to be opened by Bootstrap's JS bundle, which is no longer
 * loaded. This is the piece of it worth keeping: the menus themselves are now
 * plain Angular state, and this closes them.
 *
 * Escape closes too — Bootstrap's dropdowns did that and losing it would be a
 * regression for keyboard users.
 */
@Directive({
  selector: '[omhClickOutside]',
  standalone: false,
})
export class ClickOutsideDirective {
  @Output('omhClickOutside') clickOutside = new EventEmitter<void>();

  constructor(private el: ElementRef<HTMLElement>) {}

  @HostListener('document:mousedown', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as Node | null;
    if (target && !this.el.nativeElement.contains(target)) {
      this.clickOutside.emit();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.clickOutside.emit();
  }
}
