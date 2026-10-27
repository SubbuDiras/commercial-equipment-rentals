import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';

/**
 * Modal confirmation. With `reasonLabel` set, a reason is required and passed to `confirmed`.
 * Render it with *ngIf; content projected inside appears above the buttons.
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="dialog-backdrop" (click)="cancelled.emit()">
      <div class="dialog" role="dialog" aria-modal="true" [attr.aria-label]="title()" (click)="$event.stopPropagation()">
        <h2>{{ title() }}</h2>
        @if (message()) {
          <p>{{ message() }}</p>
        }
        <ng-content></ng-content>
        @if (reasonLabel()) {
          <label style="display:block;margin-top:1rem;">{{ reasonLabel() }}
            <textarea class="form-control" rows="3" maxlength="2000" [(ngModel)]="reason" name="reason"></textarea>
          </label>
        }
        <div class="dialog-actions">
          <button type="button" class="btn btn-secondary" (click)="cancelled.emit()" [disabled]="busy()">Back</button>
          <button type="button" class="btn" [class.btn-danger]="danger()" [class.btn-primary]="!danger()"
                  (click)="confirmed.emit(reason.trim())"
                  [disabled]="busy() || (!!reasonLabel() && !reason.trim())">
            {{ busy() ? 'Working…' : confirmLabel() }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class ConfirmDialogComponent {
  readonly title = input.required<string>();
  readonly message = input<string>('');
  readonly confirmLabel = input<string>('Confirm');
  readonly reasonLabel = input<string>('');
  readonly danger = input<boolean>(false);
  readonly busy = input<boolean>(false);

  readonly confirmed = output<string>();
  readonly cancelled = output<void>();

  reason = '';
}
