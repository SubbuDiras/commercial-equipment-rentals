import { Component } from '@angular/core';

@Component({
  selector: 'app-footer',
  standalone: true,
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
              <li><a href="#" style="color: #94a3b8;">Demolition Hammers (5-16kg)</a></li>
              <li><a href="#" style="color: #94a3b8;">Diamond Core Cutters</a></li>
              <li><a href="#" style="color: #94a3b8;">Concrete Needle Vibrators</a></li>
              <li><a href="#" style="color: #94a3b8;">Plate Compactors</a></li>
              <li><a href="#" style="color: #94a3b8;">Portable Generators (5kVA)</a></li>
            </ul>
          </div>

          <div>
            <h4 style="color: #ffffff; font-size: 0.9375rem; margin-bottom: 1rem;">Trust & Compliance</h4>
            <ul style="list-style: none; font-size: 0.875rem; display: flex; flex-direction: column; gap: 0.5rem;">
              <li><a href="#" style="color: #94a3b8;">GST Compliant B2B Invoicing</a></li>
              <li><a href="#" style="color: #94a3b8;">Verified Owner KYC</a></li>
              <li><a href="#" style="color: #94a3b8;">Escrow Settlement Protected</a></li>
              <li><a href="#" style="color: #94a3b8;">Digital Handover Checklist</a></li>
              <li><a href="#" style="color: #94a3b8;">Cancellation & Refund Policy</a></li>
            </ul>
          </div>

          <div>
            <h4 style="color: #ffffff; font-size: 0.9375rem; margin-bottom: 1rem;">Owner Community</h4>
            <p style="font-size: 0.875rem; line-height: 1.6; margin-bottom: 1rem;">
              Monetize idle machinery with guaranteed on-time payouts and damage protection.
            </p>
            <a routerLink="/owner/onboarding" class="btn btn-secondary" style="font-size: 0.8125rem; width: 100%;">
              Register as Equipment Owner
            </a>
          </div>

        </div>

        <div style="border-top: 1px solid #1e293b; padding-top: 2rem; display: flex; justify-content: space-between; align-items: center; font-size: 0.8125rem;">
          <div>© 2026 EquipRent Marketplace Technologies Private Limited. All rights reserved.</div>
          <div style="display: flex; gap: 1.5rem;">
            <a href="#" style="color: #64748b;">Terms of Rental</a>
            <a href="#" style="color: #64748b;">Privacy Policy</a>
            <a href="#" style="color: #64748b;">Security</a>
          </div>
        </div>

      </div>
    </footer>
  `
})
export class FooterComponent {}
