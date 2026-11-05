import { CommonModule } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EquipmentListing } from './equipment-catalogue.service';

/** One listing in a catalogue grid: photo, key facts, and the two ways forward (book it, or read more). */
@Component({
  selector: 'app-equipment-card',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    @let item = listing();
    <article class="card" style="display: flex; flex-direction: column; height: 100%; overflow: hidden;">
      <a [routerLink]="['/equipment', item.id]" [attr.aria-label]="item.title" style="display: block;">
        @if (item.primaryImageUrl) {
          <img [src]="item.primaryImageUrl" [alt]="item.title" loading="lazy"
               style="display: block; width: 100%; aspect-ratio: 4 / 3; object-fit: contain; background: #fff;" />
        } @else {
          <div style="display: flex; align-items: center; justify-content: center; width: 100%; aspect-ratio: 4 / 3; background: var(--color-bg-muted); color: #475569; font-size: .875rem;">
            No photo yet
          </div>
        }
      </a>
      <div style="display: flex; flex-direction: column; flex: 1; padding: .9rem 1rem 1rem;">
        <p style="color: var(--color-primary-light); font-size: .75rem; font-weight: 700;">{{ item.categoryName }}</p>
        <h2 class="line-clamp-2" style="font-size: 1rem; line-height: 1.35; margin: .3rem 0;">
          <a [routerLink]="['/equipment', item.id]" style="color: inherit;">{{ item.title }}</a>
        </h2>
        <p style="color: var(--color-text-muted); font-size: .85rem;">{{ item.manufacturer }} {{ item.modelNumber }} · {{ item.conditionGrade }}</p>
        <p style="margin: .6rem 0 .2rem;"><strong style="font-size: 1.15rem;">{{ item.baseDailyRate | currency:'INR':'symbol':'1.0-2' }}</strong> / day</p>
        <p style="color: var(--color-text-muted); font-size: .85rem;">{{ item.city }} · {{ item.minimumRentalDays }} day minimum</p>
        <div style="display: flex; flex-wrap: wrap; gap: .5rem; margin-top: auto; padding-top: .9rem;">
          @if (item.pickupSupported || item.deliverySupported) {
            <a class="btn btn-primary" [routerLink]="['/equipment', item.id, 'book']" [attr.aria-label]="'Add ' + item.title + ' to booking'"
               style="flex: 1; padding: .5rem .75rem; font-size: .875rem;">Add to booking</a>
          }
          <a class="btn btn-secondary" [routerLink]="['/equipment', item.id]" [attr.aria-label]="'View details of ' + item.title"
             style="flex: 1; padding: .5rem .75rem; font-size: .875rem;">View details</a>
        </div>
      </div>
    </article>
  `
})
export class EquipmentCardComponent {
  readonly listing = input.required<EquipmentListing>();
}
