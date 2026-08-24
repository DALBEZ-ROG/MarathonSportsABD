import { CommonModule } from '@angular/common';
import { Component, ElementRef, forwardRef, HostListener, Input } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
  selector: 'app-searchable-select',
  standalone: true,
  imports: [CommonModule, FormsModule],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SearchableSelectComponent), multi: true }],
  template: `
    <div class="search-select" [class.open]="abierto" [class.disabled]="disabled">
      <input type="text" autocomplete="off" [placeholder]="placeholder" [disabled]="disabled"
        [(ngModel)]="busqueda" (focus)="abrir()" (input)="alEscribir()" (keydown)="alTeclado($event)"
        role="combobox" [attr.aria-expanded]="abierto"/>
      <button type="button" class="toggle" tabindex="-1" (click)="alternar()" [disabled]="disabled" aria-label="Mostrar opciones"><span></span></button>
      <div class="options" *ngIf="abierto">
        <button type="button" *ngFor="let item of opcionesVisibles; let i = index" [class.active]="i === indiceActivo"
          (mousedown)="$event.preventDefault()" (click)="seleccionar(item)">{{ etiqueta(item) }}</button>
        <p class="empty" *ngIf="opcionesFiltradas.length === 0">Sin coincidencias</p>
        <p class="more" *ngIf="opcionesFiltradas.length > limite">Escriba más letras para precisar la búsqueda</p>
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; width: 100%; }
    .search-select { position: relative; width: 100%; }
    input { width: 100%; box-sizing: border-box; padding-right: 2.5rem; background: var(--ms-bg-input, #1e2430); color: var(--ms-text, #f4f4f4); border: 1px solid var(--ms-border, rgba(255,255,255,.12)); border-radius: var(--ms-radius-sm, 8px); }
    .open input { border-color: rgba(201,168,76,.65); box-shadow: 0 0 0 3px rgba(201,168,76,.09); }
    .toggle { position: absolute; right: .2rem; top: .2rem; bottom: .2rem; width: 2.2rem; border: 0; background: transparent; cursor: pointer; }
    .toggle span { display: block; width: 7px; height: 7px; margin: auto; border-right: 2px solid rgba(255,255,255,.7); border-bottom: 2px solid rgba(255,255,255,.7); transform: rotate(45deg) translate(-2px,-2px); }
    .options { position: absolute; z-index: 1200; left: 0; right: 0; top: calc(100% + 4px); max-height: 260px; overflow-y: auto; padding: .3rem; background: #17192d; border: 1px solid rgba(255,255,255,.16); border-radius: 8px; box-shadow: 0 14px 32px rgba(0,0,0,.45); }
    .options button { display: block; width: 100%; padding: .55rem .7rem; border: 0; border-radius: 5px; background: transparent; color: rgba(255,255,255,.92); text-align: left; cursor: pointer; }
    .options button:hover, .options button.active { background: rgba(201,168,76,.16); color: #fff; }
    .empty, .more { margin: 0; padding: .65rem .7rem; color: rgba(255,255,255,.46); font-size: .78rem; }
    .disabled { opacity: .6; }
  `]
})
export class SearchableSelectComponent implements ControlValueAccessor {
  @Input() items: any[] = [];
  @Input() labelKey = 'nombre';
  @Input() valueKey = '';
  @Input() placeholder = 'Escriba para buscar...';
  @Input() limite = 40;
  busqueda = '';
  abierto = false;
  disabled = false;
  indiceActivo = -1;
  private onChange: (value: any) => void = () => {};
  private onTouched: () => void = () => {};

  constructor(private elementRef: ElementRef<HTMLElement>) {}

  get opcionesFiltradas(): any[] {
    const q = this.normalizar(this.busqueda);
    return q ? this.items.filter(item => this.normalizar(this.etiqueta(item)).includes(q)) : this.items;
  }
  get opcionesVisibles(): any[] { return this.opcionesFiltradas.slice(0, this.limite); }
  etiqueta(item: any): string {
    if (!item) return '';
    if (this.labelKey.includes(',')) return this.labelKey.split(',').map(k => item[k.trim()]).filter(Boolean).join(' ');
    return String(item[this.labelKey] ?? item);
  }
  abrir(): void { if (!this.disabled) { this.abierto = true; this.indiceActivo = -1; } }
  alternar(): void { this.abierto ? this.cerrar() : this.abrir(); }
  alEscribir(): void { this.onChange(null); this.abierto = true; this.indiceActivo = -1; }
  seleccionar(item: any): void {
    const valor = this.valueKey ? item[this.valueKey] : item;
    this.busqueda = this.etiqueta(item); this.onChange(valor); this.onTouched(); this.cerrar();
  }
  alTeclado(event: KeyboardEvent): void {
    const opciones = this.opcionesVisibles;
    if (event.key === 'ArrowDown') { event.preventDefault(); this.indiceActivo = Math.min(this.indiceActivo + 1, opciones.length - 1); }
    else if (event.key === 'ArrowUp') { event.preventDefault(); this.indiceActivo = Math.max(this.indiceActivo - 1, 0); }
    else if (event.key === 'Enter' && this.indiceActivo >= 0) { event.preventDefault(); this.seleccionar(opciones[this.indiceActivo]); }
    else if (event.key === 'Escape') this.cerrar();
  }
  cerrar(): void { this.abierto = false; this.indiceActivo = -1; this.onTouched(); }
  writeValue(value: any): void {
    const item = this.items.find(opcion => this.valueKey ? opcion?.[this.valueKey] === value : opcion === value);
    this.busqueda = item ? this.etiqueta(item) : '';
  }
  registerOnChange(fn: (value: any) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(disabled: boolean): void { this.disabled = disabled; }
  @HostListener('document:mousedown', ['$event'])
  clickFuera(event: MouseEvent): void { if (!this.elementRef.nativeElement.contains(event.target as Node)) this.cerrar(); }
  private normalizar(value: string): string { return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase().trim(); }
}
