import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="login-page">
      <!-- Animated background orbs -->
      <div class="bg-orb orb-1"></div>
      <div class="bg-orb orb-2"></div>
      <div class="bg-orb orb-3"></div>

      <div class="login-container">
        <!-- Glass Card -->
        <div class="glass-card">
          <!-- Top accent line -->
          <div class="accent-line"></div>

          <div class="card-content">
            <!-- Logo & Brand -->
            <div class="brand-section">
              <div class="logo-icon">
                <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
                  <circle cx="24" cy="24" r="22" stroke="url(#gold-gradient)" stroke-width="2.5" fill="none"/>
                  <path d="M16 28 L22 20 L28 26 L34 18" stroke="url(#gold-gradient)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
                  <circle cx="16" cy="28" r="2" fill="url(#gold-gradient)"/>
                  <circle cx="34" cy="18" r="2" fill="url(#gold-gradient)"/>
                  <defs>
                    <linearGradient id="gold-gradient" x1="0%" y1="0%" x2="100%" y2="100%">
                      <stop offset="0%" stop-color="#C9A84C"/>
                      <stop offset="50%" stop-color="#F4E28D"/>
                      <stop offset="100%" stop-color="#C9A84C"/>
                    </linearGradient>
                  </defs>
                </svg>
              </div>
              <h1 class="brand-name">Marathon Sports</h1>
              <p class="brand-tagline">Sistema de Gestión de Pedidos</p>
            </div>

            <!-- Login Form -->
            <form (ngSubmit)="onLogin()" class="login-form">
              <div class="input-group">
                <div class="input-wrapper">
                  <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/>
                    <polyline points="22,6 12,13 2,6"/>
                  </svg>
                  <input
                    id="correo"
                    type="email"
                    [(ngModel)]="correo"
                    name="correo"
                    placeholder="Correo electrónico"
                    [disabled]="loading"
                    required
                    autocomplete="email"
                  />
                </div>
              </div>

              <div class="input-group">
                <div class="input-wrapper">
                  <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                    <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                    <circle cx="12" cy="16" r="1"/>
                  </svg>
                  <input
                    id="password"
                    [type]="showPassword ? 'text' : 'password'"
                    [(ngModel)]="password"
                    name="password"
                    placeholder="Contraseña"
                    [disabled]="loading"
                    required
                    autocomplete="current-password"
                  />
                  <button type="button" class="toggle-password" (click)="showPassword = !showPassword" tabindex="-1">
                    <svg *ngIf="!showPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                      <circle cx="12" cy="12" r="3"/>
                    </svg>
                    <svg *ngIf="showPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                      <line x1="1" y1="1" x2="23" y2="23"/>
                    </svg>
                  </button>
                </div>
              </div>

              <!-- Error -->
              <div class="error-toast" *ngIf="errorMsg">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <line x1="15" y1="9" x2="9" y2="15"/>
                  <line x1="9" y1="9" x2="15" y2="15"/>
                </svg>
                <span>{{ errorMsg }}</span>
              </div>

              <!-- Submit Button -->
              <button type="submit" class="btn-submit" [disabled]="loading" [class.is-loading]="loading">
                <span class="btn-text" *ngIf="!loading">Iniciar Sesión</span>
                <div class="btn-loader" *ngIf="loading">
                  <div class="loader-dot"></div>
                  <div class="loader-dot"></div>
                  <div class="loader-dot"></div>
                </div>
              </button>
            </form>

            <!-- Footer -->
            <div class="card-footer">
              <p>&copy; 2024 Marathon Sports — Todos los derechos reservados</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    /* ═══════════════════════════════════════════
       PREMIUM GLASSMORPHISM LOGIN — DUBAI STYLE
       ═══════════════════════════════════════════ */

    :host {
      display: block;
      height: 100vh;
    }

    .login-page {
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      background: linear-gradient(160deg, #0a0a0f 0%, #0f1623 40%, #0d0d14 100%);
      overflow: hidden;
      padding: 1.5rem;
    }

    /* ── Animated Background Orbs ── */
    .bg-orb {
      position: absolute;
      border-radius: 50%;
      filter: blur(80px);
      opacity: 0.22;
      animation: float 20s ease-in-out infinite;
    }

    .orb-1 {
      width: 500px;
      height: 500px;
      background: radial-gradient(circle, #1a3a5c 0%, transparent 70%);
      top: -15%;
      right: -10%;
      animation-delay: 0s;
    }

    .orb-2 {
      width: 400px;
      height: 400px;
      background: radial-gradient(circle, #1a5c3a 0%, transparent 70%);
      bottom: -10%;
      left: -10%;
      animation-delay: -7s;
    }

    .orb-3 {
      width: 300px;
      height: 300px;
      background: radial-gradient(circle, #C9A84C 0%, transparent 70%);
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      opacity: 0.08;
      animation-delay: -14s;
    }

    @keyframes float {
      0%, 100% { transform: translate(0, 0) scale(1); }
      33% { transform: translate(30px, -30px) scale(1.05); }
      66% { transform: translate(-20px, 20px) scale(0.95); }
    }

    /* ── Login Container ── */
    .login-container {
      position: relative;
      z-index: 10;
      width: 100%;
      max-width: 420px;
    }

    /* ── Glass Card ── */
    .glass-card {
      position: relative;
      background: rgba(255, 255, 255, 0.03);
      backdrop-filter: blur(20px);
      -webkit-backdrop-filter: blur(20px);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 24px;
      overflow: hidden;
      box-shadow:
        0 25px 50px rgba(0, 0, 0, 0.5),
        0 0 0 1px rgba(255, 255, 255, 0.05) inset,
        0 1px 0 rgba(255, 255, 255, 0.1) inset;
    }

    .accent-line {
      height: 2px;
      background: linear-gradient(90deg, transparent, rgba(201, 168, 76, 0.7), transparent);
    }

    .card-content {
      padding: 2.5rem 2.5rem 2rem;
    }

    /* ── Brand Section ── */
    .brand-section {
      text-align: center;
      margin-bottom: 2.5rem;
    }

    .logo-icon {
      margin-bottom: 1rem;
      display: inline-block;
      animation: pulse-glow 3s ease-in-out infinite;
    }

    @keyframes pulse-glow {
      0%, 100% { filter: drop-shadow(0 0 6px rgba(201, 168, 76, 0.15)); }
      50% { filter: drop-shadow(0 0 10px rgba(201, 168, 76, 0.28)); }
    }

    .brand-name {
      font-family: 'Segoe UI', system-ui, sans-serif;
      font-size: 1.75rem;
      font-weight: 400;
      letter-spacing: 2px;
      text-transform: uppercase;
      color: #fff;
      margin-bottom: 0.4rem;
    }

    .brand-tagline {
      font-size: 0.8rem;
      color: rgba(255, 255, 255, 0.4);
      letter-spacing: 1px;
      text-transform: uppercase;
      font-weight: 300;
    }

    /* ── Form Styles ── */
    .login-form {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }

    .input-group {
      position: relative;
    }

    .input-wrapper {
      position: relative;
      display: flex;
      align-items: center;
    }

    .input-icon {
      position: absolute;
      left: 1rem;
      color: rgba(255, 255, 255, 0.35);
      pointer-events: none;
      transition: color 0.3s ease;
    }

    .input-wrapper input {
      width: 100%;
      padding: 1rem 3rem 1rem 3rem;
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 12px;
      color: #ffffff;
      font-size: 0.95rem;
      font-weight: 300;
      letter-spacing: 0.3px;
      outline: none;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    }

    .input-wrapper input::placeholder {
      color: rgba(255, 255, 255, 0.25);
      font-weight: 300;
    }

    .input-wrapper input:focus {
      background: rgba(255, 255, 255, 0.07);
      border-color: rgba(201, 168, 76, 0.5);
      box-shadow: 0 0 0 3px rgba(201, 168, 76, 0.1), 0 4px 16px rgba(0, 0, 0, 0.2);
    }

    .input-wrapper input:focus ~ .input-icon,
    .input-wrapper input:focus + .input-icon {
      color: rgba(201, 168, 76, 1);
    }

    .input-wrapper:focus-within .input-icon {
      color: #C9A84C;
    }

    .input-wrapper input:disabled {
      opacity: 0.5;
    }

    .toggle-password {
      position: absolute;
      right: 1rem;
      background: none;
      border: none;
      color: rgba(255, 255, 255, 0.3);
      cursor: pointer;
      padding: 4px;
      display: flex;
      align-items: center;
      transition: color 0.3s;
    }

    .toggle-password:hover {
      color: rgba(255, 255, 255, 0.7);
    }

    /* ── Error Toast ── */
    .error-toast {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      padding: 0.75rem 1rem;
      background: rgba(220, 38, 38, 0.1);
      border: 1px solid rgba(220, 38, 38, 0.25);
      border-radius: 10px;
      color: #fca5a5;
      font-size: 0.85rem;
      font-weight: 300;
      animation: shake 0.4s ease;
    }

    @keyframes shake {
      0%, 100% { transform: translateX(0); }
      25% { transform: translateX(-4px); }
      75% { transform: translateX(4px); }
    }

    /* ── Submit Button ── */
    .btn-submit {
      position: relative;
      width: 100%;
      padding: 1rem;
      margin-top: 0.5rem;
      background: linear-gradient(135deg, #C9A84C 0%, #a08339 50%, #C9A84C 100%);
      background-size: 200% 200%;
      border: none;
      border-radius: 12px;
      color: #0a0a0a;
      font-size: 0.95rem;
      font-weight: 600;
      letter-spacing: 1px;
      text-transform: uppercase;
      cursor: pointer;
      overflow: hidden;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      box-shadow: 0 4px 16px rgba(201, 168, 76, 0.3);
    }

    .btn-submit::before {
      content: '';
      position: absolute;
      top: 0;
      left: -100%;
      width: 100%;
      height: 100%;
      background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
      transition: left 0.5s ease;
    }

    .btn-submit:hover:not(:disabled) {
      background-position: 100% 0;
      box-shadow: 0 8px 32px rgba(201, 168, 76, 0.4);
      transform: translateY(-1px);
    }

    .btn-submit:hover:not(:disabled)::before {
      left: 100%;
    }

    .btn-submit:active:not(:disabled) {
      transform: translateY(0);
    }

    .btn-submit:disabled {
      opacity: 0.7;
      cursor: not-allowed;
      transform: none;
    }

    .btn-submit.is-loading {
      background: linear-gradient(135deg, rgba(201, 168, 76, 0.6) 0%, rgba(160, 131, 57, 0.6) 100%);
    }

    .btn-text {
      position: relative;
      z-index: 1;
    }

    /* ── Loader Dots ── */
    .btn-loader {
      display: flex;
      gap: 6px;
      justify-content: center;
      align-items: center;
    }

    .loader-dot {
      width: 8px;
      height: 8px;
      background: #0a0a0a;
      border-radius: 50%;
      animation: bounce 1.2s ease-in-out infinite;
    }

    .loader-dot:nth-child(2) { animation-delay: 0.2s; }
    .loader-dot:nth-child(3) { animation-delay: 0.4s; }

    @keyframes bounce {
      0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
      40% { transform: scale(1); opacity: 1; }
    }

    /* ── Footer ── */
    .card-footer {
      text-align: center;
      margin-top: 2rem;
      padding-top: 1.5rem;
      border-top: 1px solid rgba(255, 255, 255, 0.05);
    }

    .card-footer p {
      font-size: 0.7rem;
      color: rgba(255, 255, 255, 0.2);
      letter-spacing: 0.5px;
      font-weight: 300;
    }

    /* ── Responsive ── */
    @media (max-width: 480px) {
      .card-content {
        padding: 2rem 1.5rem 1.5rem;
      }

      .brand-name {
        font-size: 1.4rem;
      }

      .glass-card {
        border-radius: 20px;
      }
    }

    @media (min-width: 768px) {
      .glass-card {
        transition: transform 0.3s ease, box-shadow 0.3s ease;
      }

      .glass-card:hover {
        transform: translateY(-4px);
        box-shadow:
          0 30px 60px rgba(0, 0, 0, 0.6),
          0 0 0 1px rgba(255, 255, 255, 0.05) inset,
          0 1px 0 rgba(255, 255, 255, 0.1) inset;
      }
    }
  `]
})
export class LoginComponent {
  correo = '';
  password = '';
  loading = false;
  errorMsg = '';
  showPassword = false;

  constructor(private authService: AuthService, private router: Router) {
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/inicio']);
    }
  }

  onLogin(): void {
    if (!this.correo || !this.password) {
      this.errorMsg = 'Complete todos los campos';
      return;
    }

    this.loading = true;
    this.errorMsg = '';

    this.authService.login(this.correo, this.password).subscribe({
      next: () => {
        this.loading = false;
        this.router.navigate(['/inicio']);
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 400) {
          this.errorMsg = err.error?.message || 'Credenciales incorrectas';
        } else if (err.status === 403) {
          this.errorMsg = 'Usuario inactivo';
        } else {
          this.errorMsg = 'Error de conexión con el servidor';
        }
      }
    });
  }
}
