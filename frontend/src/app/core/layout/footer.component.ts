import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [RouterLink],
  template: `
    <footer style="background-color: #0f172a; color: #94a3b8; padding-top: 4rem; padding-bottom: 3rem; margin-top: 6rem; border-top: 1px solid #1e293b;">
      <div class="container">
        <div class="grid grid-cols-4" style="margin-bottom: 3rem;">

          <div>
            <div style="font-family: var(--font-heading); font-size: 1.25rem; font-weight: 800; color: #ffffff; margin-bottom: 1rem;">
              EquipRent<span style="color: #3b82f6;">India</span>
            </div>
            <p style="font-size: 0.875rem; line-height: 1.6; margin-bottom: 1rem;">
              India's premier asset-light commercial equipment rental marketplace. Serving construction contractors, subcontractors, and industrial yards.
            </p>
            <div style="display: flex; gap: 0.5rem; align-items: center;">
              <span class="badge badge-verified">Pilot Active</span>
              <span style="font-size: 0.8125rem; color: #cbd5e1;">Bengaluru Corridor</span>
            </div>
          </div>

          <div>
            <h4 style="color: #ffffff; font-size: 0.9375rem; margin-bottom: 1rem;">Pilot Categories</h4>
            <ul style="list-style: none; font-size: 0.875rem; display: flex; flex-direction: column; gap: 0.5rem;">
              <li>Demolition Hammers (5-16kg)</li>
              <li>Diamond Core Cutters</li>
              <li>Concrete Needle Vibrators</li>
              <li>Plate Compactors</li>
              <li>Portable Generators (5kVA)</li>
            </ul>
            <a routerLink="/equipment" style="display: inline-block; margin-top: 0.75rem; color: #93c5fd; font-size: 0.875rem;">Browse all equipment →</a>
          </div>

          <div>
            <h4 style="color: #ffffff; font-size: 0.9375rem; margin-bottom: 1rem;">How rentals work</h4>
            <ul style="list-style: none; font-size: 0.875rem; display: flex; flex-direction: column; gap: 0.5rem;">
              <li>Owners are KYC-verified before listing</li>
              <li>Every listing is reviewed before it goes live</li>
              <li>Pay online once the owner accepts</li>
              <li>Handover and return with photos and one-time codes</li>
              <li>Deposit refunded after the return inspection</li>
            </ul>
          </div>

          <div>
            <h4 style="color: #ffffff; font-size: 0.9375rem; margin-bottom: 1rem;">Owner Community</h4>
            <p style="font-size: 0.875rem; line-height: 1.6; margin-bottom: 1rem;">
              Monetize idle machinery: list it, accept requests, and get paid after each rental.
            </p>
            <a routerLink="/owner/onboarding" class="btn btn-secondary" style="font-size: 0.8125rem; width: 100%;">
              Register as Equipment Owner
            </a>
          </div>

        </div>

        <div style="border-top: 1px solid #1e293b; padding-top: 2rem; font-size: 0.8125rem;">
          <div>© 2026 EquipRent Marketplace Technologies Private Limited. All rights reserved.</div>
        </div>

      </div>
    </footer>
  `
})
export class FooterComponent {}
