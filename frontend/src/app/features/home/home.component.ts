import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';

interface HealthResponse {
  status: string;
  service: string;
  version: string;
  timestamp: string;
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div>
      <!-- Backend Health Status Banner -->
      <div *ngIf="backendHealth()" style="background-color: #ecfdf5; border-bottom: 1px solid #a7f3d0; padding: 0.5rem 0;">
        <div class="container" style="display: flex; justify-content: space-between; align-items: center; font-size: 0.8125rem; color: #065f46;">
          <div style="display: flex; align-items: center; gap: 0.5rem;">
            <span style="display: inline-block; width: 8px; height: 8px; background-color: #10b981; border-radius: 50%;"></span>
            <span><strong>Backend Connected:</strong> {{ backendHealth()?.service }} ({{ backendHealth()?.version }}) - Status: {{ backendHealth()?.status }}</span>
          </div>
          <span>Bengaluru Pilot Preparation</span>
        </div>
      </div>

      <!-- Hero Section -->
      <section style="background: linear-gradient(180deg, #f8fafc 0%, #e2e8f0 100%); padding: 5rem 0 6rem 0; border-bottom: 1px solid var(--color-border);">
        <div class="container" style="text-align: center; max-width: 900px;">
          
          <div style="display: inline-flex; align-items: center; gap: 0.5rem; background-color: #eff6ff; color: #1e40af; padding: 0.375rem 0.875rem; border-radius: var(--radius-full); font-size: 0.8125rem; font-weight: 600; margin-bottom: 1.5rem; border: 1px solid #bfdbfe;">
            <span>Commercial Equipment Rental — Bengaluru Pilot Preparation</span>
          </div>

          <h1 style="font-size: 3.25rem; font-weight: 800; letter-spacing: -0.03em; line-height: 1.15; margin-bottom: 1.25rem; color: #0f172a;">
            Rent Commercial Equipment.<br>
            <span style="background: linear-gradient(135deg, #1e3a8a, #2563eb); -webkit-background-clip: text; -webkit-text-fill-color: transparent;">
              Zero Idle Capital. Maximum Site Uptime.
            </span>
          </h1>

          <p style="font-size: 1.125rem; color: var(--color-text-muted); line-height: 1.6; margin-bottom: 2.5rem;">
            We are preparing a Bengaluru marketplace for commercial equipment rentals. Listings, availability, final prices, and invoicing will be shown when owner verification and pilot launch checks are complete.
          </p>

          <!-- Search Filter Box -->
          <div class="card" style="padding: 1.5rem; box-shadow: var(--shadow-xl); border-radius: var(--radius-xl); text-align: left;">
            <p style="margin: 0 0 1rem; color: var(--color-text-muted); font-size: 0.875rem;">
              Catalogue search is not available during pilot preparation.
            </p>
            <div class="grid grid-cols-4" style="gap: 1rem; align-items: flex-end;">
              
              <div>
                <label style="display: block; font-size: 0.75rem; font-weight: 700; text-transform: uppercase; color: var(--color-text-muted); margin-bottom: 0.5rem;">
                  Equipment Category
                </label>
                <select disabled [(ngModel)]="selectedCategory" style="width: 100%; padding: 0.625rem; border-radius: var(--radius-md); border: 1px solid var(--color-border); font-size: 0.875rem;">
                  <option value="all">All Pilot Categories</option>
                  <option value="demolition">Demolition Hammers (5-16kg)</option>
                  <option value="core-cutters">Core Cutting Machines</option>
                  <option value="compactors">Plate Compactors</option>
                  <option value="generators">Portable Generators (5kVA)</option>
                  <option value="vibrators">Concrete Needle Vibrators</option>
                </select>
              </div>

              <div>
                <label style="display: block; font-size: 0.75rem; font-weight: 700; text-transform: uppercase; color: var(--color-text-muted); margin-bottom: 0.5rem;">
                  Jobsite Location
                </label>
                <input disabled type="text" [(ngModel)]="selectedLocation" placeholder="Bengaluru / Whitefield / EC" style="width: 100%; padding: 0.625rem; border-radius: var(--radius-md); border: 1px solid var(--color-border); font-size: 0.875rem;" />
              </div>

              <div>
                <label style="display: block; font-size: 0.75rem; font-weight: 700; text-transform: uppercase; color: var(--color-text-muted); margin-bottom: 0.5rem;">
                  Rental Duration
                </label>
                <input disabled type="date" [(ngModel)]="startDate" style="width: 100%; padding: 0.625rem; border-radius: var(--radius-md); border: 1px solid var(--color-border); font-size: 0.875rem;" />
              </div>

              <div>
                <button disabled class="btn btn-primary" id="btn-search-equipment" style="width: 100%; height: 2.625rem;">
                  Catalogue not yet available
                </button>
              </div>

            </div>
          </div>

        </div>
      </section>

      <!-- Pilot Categories Showcase -->
      <section style="padding: 5rem 0;">
        <div class="container">
          <div style="display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 2.5rem;">
            <div>
              <div style="font-size: 0.8125rem; font-weight: 700; text-transform: uppercase; color: var(--color-primary-light); margin-bottom: 0.25rem;">
                Illustrative Category Preview
              </div>
              <h2 style="font-size: 2rem;">Small Construction Equipment (Bengaluru Pilot)</h2>
            </div>
            <span style="font-weight: 600; font-size: 0.875rem; color: var(--color-text-muted);">Listings will appear after launch approval</span>
          </div>

          <div class="grid grid-cols-3">
            
            <!-- Category Card 1 -->
            <div class="card" style="padding: 1.5rem; display: flex; flex-direction: column; justify-content: space-between;">
              <div>
                <div style="font-size: 2rem; margin-bottom: 1rem;">🔨</div>
                <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 0.5rem;">
                  <h3 style="font-size: 1.125rem;">Demolition Hammers & Breakers</h3>
                  <span class="badge badge-role">Category preview</span>
                </div>
                <p style="font-size: 0.875rem; color: var(--color-text-muted); margin-bottom: 1.25rem;">
                  5kg to 16kg heavy breakers (Bosch, Hilti, Stanley) with SDS-max chisels. Ideal for RCC demolition and floor breaking.
                </p>
              </div>
              <div style="border-top: 1px solid var(--color-border); padding-top: 1rem; display: flex; justify-content: space-between; align-items: center;">
                <div>
                  <span style="font-size: 0.875rem; color: var(--color-text-muted);">Pricing to be confirmed</span>
                </div>
                <span class="badge badge-role">Not yet listed</span>
              </div>
            </div>

            <!-- Category Card 2 -->
            <div class="card" style="padding: 1.5rem; display: flex; flex-direction: column; justify-content: space-between;">
              <div>
                <div style="font-size: 2rem; margin-bottom: 1rem;">🕳️</div>
                <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 0.5rem;">
                  <h3 style="font-size: 1.125rem;">Diamond Core Cutters</h3>
                  <span class="badge badge-role">Category preview</span>
                </div>
                <p style="font-size: 0.875rem; color: var(--color-text-muted); margin-bottom: 1.25rem;">
                  Core drilling rigs up to 200mm diameter with stand & diamond core barrels for plumbing, HVAC, and electrical penetrations.
                </p>
              </div>
              <div style="border-top: 1px solid var(--color-border); padding-top: 1rem; display: flex; justify-content: space-between; align-items: center;">
                <div>
                  <span style="font-size: 0.875rem; color: var(--color-text-muted);">Pricing to be confirmed</span>
                </div>
                <span class="badge badge-role">Not yet listed</span>
              </div>
            </div>

            <!-- Category Card 3 -->
            <div class="card" style="padding: 1.5rem; display: flex; flex-direction: column; justify-content: space-between;">
              <div>
                <div style="font-size: 2rem; margin-bottom: 1rem;">⚡</div>
                <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 0.5rem;">
                  <h3 style="font-size: 1.125rem;">Portable Diesel Generators</h3>
                  <span class="badge badge-role">Category preview</span>
                </div>
                <p style="font-size: 0.875rem; color: var(--color-text-muted); margin-bottom: 1.25rem;">
                  3kVA to 10kVA silent diesel generators with electric start for continuous site power backup and mobile welding works.
                </p>
              </div>
              <div style="border-top: 1px solid var(--color-border); padding-top: 1rem; display: flex; justify-content: space-between; align-items: center;">
                <div>
                  <span style="font-size: 0.875rem; color: var(--color-text-muted);">Pricing to be confirmed</span>
                </div>
                <span class="badge badge-role">Not yet listed</span>
              </div>
            </div>

          </div>
        </div>
      </section>

      <!-- Trust & Chain of Custody Section -->
      <section style="background-color: var(--color-bg-muted); padding: 5rem 0; border-top: 1px solid var(--color-border);">
        <div class="container">
          <div style="text-align: center; max-width: 700px; margin: 0 auto 3.5rem auto;">
            <h2 style="font-size: 2rem; margin-bottom: 0.75rem;">Engineered for Construction Integrity</h2>
            <p style="color: var(--color-text-muted);">Operational safeguards are being prepared and will be validated before the pilot opens.</p>
          </div>

          <div class="grid grid-cols-4">
            <div class="card" style="padding: 1.5rem; background: white;">
              <div style="font-size: 1.5rem; margin-bottom: 0.75rem;">🛡️</div>
              <h4 style="font-size: 1rem; margin-bottom: 0.5rem;">Owner Verification</h4>
              <p style="font-size: 0.8125rem; color: var(--color-text-muted); line-height: 1.5;">Owner identity, business details, payout information, and equipment records are launch prerequisites.</p>
            </div>

            <div class="card" style="padding: 1.5rem; background: white;">
              <div style="font-size: 1.5rem; margin-bottom: 0.75rem;">📸</div>
              <h4 style="font-size: 1rem; margin-bottom: 0.5rem;">Photo Inspections</h4>
              <p style="font-size: 0.8125rem; color: var(--color-text-muted); line-height: 1.5;">Handover and return inspection workflows are implemented; the full operational journey still requires end-to-end validation.</p>
            </div>

            <div class="card" style="padding: 1.5rem; background: white;">
              <div style="font-size: 1.5rem; margin-bottom: 0.75rem;">💳</div>
              <h4 style="font-size: 1rem; margin-bottom: 0.5rem;">Payment Settlement</h4>
              <p style="font-size: 0.8125rem; color: var(--color-text-muted); line-height: 1.5;">Settlement and refund workflows require live provider configuration and reconciliation before launch.</p>
            </div>

            <div class="card" style="padding: 1.5rem; background: white;">
              <div style="font-size: 1.5rem; margin-bottom: 0.75rem;">🧾</div>
              <h4 style="font-size: 1rem; margin-bottom: 0.5rem;">Tax Readiness</h4>
              <p style="font-size: 0.8125rem; color: var(--color-text-muted); line-height: 1.5;">Invoice generation and GST treatment need tax-adviser approval and production validation before any tax claims are made.</p>
            </div>
          </div>
        </div>
      </section>
    </div>
  `
})
export class HomeComponent implements OnInit {
  private http = inject(HttpClient);
  
  selectedCategory = 'all';
  selectedLocation = 'Bengaluru Urban';
  startDate = new Date().toISOString().split('T')[0];
  backendHealth = signal<HealthResponse | null>(null);

  ngOnInit(): void {
    // Check local backend health connectivity
    this.http.get<HealthResponse>('/api/v1/health')
      .subscribe({
        next: (data) => this.backendHealth.set(data),
        error: () => console.log('Backend not reachable yet (expected until service started)')
      });
  }

}
