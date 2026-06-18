import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="perfil-container">
      <div class="perfil-card">
        <h2>Mi Perfil</h2>
        <div class="info-section">
          <div class="info-row"><span class="label">Nombre:</span><span>{{user?.nombre}} {{user?.apellido}}</span></div>
          <div class="info-row"><span class="label">Correo:</span><span>{{user?.correo}}</span></div>
          <div class="info-row"><span class="label">Rol:</span><span class="role-badge">{{user?.rol}}</span></div>
        </div>

        <h3>Cambiar contraseña</h3>
        <form (ngSubmit)="cambiarPassword()">
          <div class="form-group"><label>Contraseña actual</label><input type="password" [(ngModel)]="form.passwordActual" name="pa" required/></div>
          <div class="form-group"><label>Nueva contraseña</label><input type="password" [(ngModel)]="form.passwordNuevo" name="pn" required minlength="8"/></div>
          <div class="form-group"><label>Confirmar</label><input type="password" [(ngModel)]="form.confirmarPassword" name="pc" required/></div>
          <small class="error" *ngIf="error">{{error}}</small>
          <small class="success" *ngIf="success">{{success}}</small>
          <button type="submit" class="btn-save" [disabled]="saving">{{saving?'Guardando...':'Cambiar contraseña'}}</button>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .perfil-container{max-width:500px;margin:2rem auto;padding:0 1rem}
    .perfil-card{background:#fff;border-radius:12px;padding:2rem;box-shadow:0 2px 12px rgba(0,0,0,.08)}
    h2{color:#2d5a27;margin-bottom:1rem}
    h3{color:#2d5a27;margin:1.5rem 0 1rem;padding-top:1rem;border-top:1px solid #eee}
    .info-section{margin-bottom:1rem}
    .info-row{display:flex;gap:.5rem;margin-bottom:.5rem;font-size:.9rem}
    .label{font-weight:600;min-width:80px}
    .role-badge{background:#e8f5e9;color:#2d5a27;padding:.2rem .6rem;border-radius:10px;font-size:.8rem}
    .form-group{margin-bottom:.8rem;display:flex;flex-direction:column;gap:.3rem}
    .form-group label{font-size:.85rem;font-weight:600}
    .form-group input{padding:.6rem;border:1px solid #ddd;border-radius:4px}
    .error{color:#c00;font-size:.8rem;display:block;margin-bottom:.5rem}
    .success{color:#2d5a27;font-size:.8rem;display:block;margin-bottom:.5rem}
    .btn-save{width:100%;padding:.7rem;border:none;border-radius:6px;background:#2d5a27;color:#fff;cursor:pointer;font-weight:600}
    .btn-save:disabled{opacity:.6}
  `]
})
export class PerfilComponent {
  user: any;
  form = { passwordActual: '', passwordNuevo: '', confirmarPassword: '' };
  error = ''; success = ''; saving = false;

  constructor(private authService: AuthService, private http: HttpClient) {
    this.user = this.authService.getCurrentUser();
  }

  cambiarPassword() {
    this.error = ''; this.success = '';
    if (this.form.passwordNuevo !== this.form.confirmarPassword) { this.error = 'Las contraseñas no coinciden'; return; }
    if (this.form.passwordNuevo.length < 8) { this.error = 'Mínimo 8 caracteres'; return; }
    this.saving = true;
    this.http.put(`${environment.apiUrl}/usuarios/${this.user.idUsuario}/password`, this.form).subscribe({
      next: () => { this.saving = false; this.success = 'Contraseña actualizada correctamente'; this.form = {passwordActual:'',passwordNuevo:'',confirmarPassword:''}; },
      error: e => { this.saving = false; this.error = e.error?.message || 'Error al cambiar contraseña'; }
    });
  }
}
