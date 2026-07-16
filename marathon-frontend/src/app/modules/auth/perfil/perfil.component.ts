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
    /* Inherits global dark theme from styles.scss */
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
