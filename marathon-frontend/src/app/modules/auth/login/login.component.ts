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
      <div class="login-card">
        <div class="logo-section">
          <h1 class="brand">Marathon Sports</h1>
          <p class="subtitle">Sistema de Gestión de Pedidos</p>
        </div>

        <form (ngSubmit)="onLogin()" class="login-form">
          <div class="form-group">
            <label for="correo">Correo electrónico</label>
            <input
              id="correo"
              type="email"
              [(ngModel)]="correo"
              name="correo"
              placeholder="admin@marathon.com"
              [disabled]="loading"
              required
              autocomplete="email"
            />
          </div>

          <div class="form-group">
            <label for="password">Contraseña</label>
            <input
              id="password"
              type="password"
              [(ngModel)]="password"
              name="password"
              placeholder="••••••••"
              [disabled]="loading"
              required
              autocomplete="current-password"
            />
          </div>

          <div class="error-message" *ngIf="errorMsg">
            {{ errorMsg }}
          </div>

          <button type="submit" class="btn-login" [disabled]="loading">
            <span *ngIf="!loading">Iniciar sesión</span>
            <span *ngIf="loading" class="spinner"></span>
          </button>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .login-page {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      background-color: #2d5a27;
      padding: 1rem;
    }

    .login-card {
      background: #ffffff;
      border-radius: 12px;
      padding: 2.5rem;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
      width: 100%;
      max-width: 400px;
    }

    .logo-section {
      text-align: center;
      margin-bottom: 2rem;
    }

    .brand {
      color: #2d5a27;
      font-size: 1.8rem;
      font-weight: 700;
      margin-bottom: 0.25rem;
    }

    .subtitle {
      color: #666;
      font-size: 0.9rem;
    }

    .login-form {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }

    .form-group label {
      font-size: 0.85rem;
      font-weight: 600;
      color: #333;
    }

    .form-group input {
      padding: 0.75rem 1rem;
      border: 1px solid #ddd;
      border-radius: 6px;
      font-size: 0.95rem;
      transition: border-color 0.2s;
      outline: none;
    }

    .form-group input:focus {
      border-color: #2d5a27;
    }

    .form-group input:disabled {
      background-color: #f5f5f5;
    }

    .error-message {
      background-color: #fee;
      color: #c00;
      padding: 0.6rem 1rem;
      border-radius: 6px;
      font-size: 0.85rem;
      text-align: center;
    }

    .btn-login {
      background-color: #2d5a27;
      color: #fff;
      padding: 0.85rem;
      border: none;
      border-radius: 6px;
      font-size: 1rem;
      font-weight: 600;
      cursor: pointer;
      transition: background-color 0.2s;
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 48px;
    }

    .btn-login:hover:not(:disabled) {
      background-color: #1e3d1a;
    }

    .btn-login:disabled {
      opacity: 0.7;
      cursor: not-allowed;
    }

    .spinner {
      width: 20px;
      height: 20px;
      border: 2px solid #ffffff44;
      border-top-color: #fff;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    @media (max-width: 480px) {
      .login-card {
        padding: 1.5rem;
      }

      .brand {
        font-size: 1.5rem;
      }
    }
  `]
})
export class LoginComponent {
  correo = '';
  password = '';
  loading = false;
  errorMsg = '';

  constructor(private authService: AuthService, private router: Router) {
    // Si ya está autenticado, redirigir al dashboard
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/dashboard']);
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
        this.router.navigate(['/dashboard']);
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
