import { CommonModule } from '@angular/common';
import { Component, DoCheck, ElementRef, EventEmitter, forwardRef, HostListener, Input, OnDestroy, Output } from '@angular/core';
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
        <p class="empty" *ngIf="buscando">Buscando…</p>
        <p class="empty" *ngIf="!buscando && totalFiltradas === 0">Sin coincidencias</p>
        <p class="more" *ngIf="!buscando && remoto && totalFiltradas >= limite">Hay más: escriba unas letras más para precisar</p>
        <p class="more" *ngIf="!buscando && !remoto && totalFiltradas > limite">Escriba más letras para precisar la búsqueda</p>
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; width: 100%; }
    .search-select { position: relative; width: 100%; }
    /* El tamaño va AQUI, no en la pagina que lo usa.
       Antes solo se declaraba el hueco de la flecha, y el alto lo ponia la
       regla global .form-group input. Donde no hay un .form-group alrededor
       —el picking, por ejemplo— la caja se quedaba en 19 px: una raya, al lado
       de los 44 px que mide cualquier otro buscador de la aplicacion. Un
       componente compartido no puede depender de como este montada la pantalla
       que lo mete. Estas son las medidas de la casa, las mismas que ya calculaba
       la regla global: .7rem/1rem de relleno y .9rem de letra. */
    input { width: 100%; box-sizing: border-box; padding: .7rem 2.5rem .7rem 1rem;
            font-family: inherit; font-size: .9rem;
            background: var(--ms-bg-input, #1e2430); color: var(--ms-text, #f4f4f4);
            border: 1px solid var(--ms-border, rgba(255,255,255,.12));
            border-radius: var(--ms-radius-sm, 8px); }
    input::placeholder { color: var(--ms-text-muted, rgba(255,255,255,.4)); }
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
export class SearchableSelectComponent implements ControlValueAccessor, OnDestroy, DoCheck {
  /**
   * F93: el filtrado dejó de ser un getter llamado desde la plantilla.
   *
   * Lo era, y con listas grandes es una trampa doble. Angular evalúa las
   * expresiones de la plantilla en CADA ciclo de detección de cambios —no solo
   * al escribir— y el getter recorría la lista entera normalizando acentos
   * cadena por cadena. Con los 1,4 millones de clientes que servía
   * `/clientes/activos`, eso son 1,4 millones de `normalize('NFD')` más una
   * expresión regular por tecla pulsada, por movimiento de ratón y por
   * cualquier otra cosa que disparase un ciclo. El navegador se colgaba.
   *
   * Ahora el resultado se calcula UNA vez, cuando cambia lo escrito o la lista,
   * y la plantilla lee un array ya hecho.
   */
  private _items: any[] = [];
  @Input() set items(valor: any[]) {
    this._items = valor ?? [];
    this.recalcular();
  }
  get items(): any[] { return this._items; }

  @Input() labelKey = 'nombre';
  @Input() valueKey = '';
  @Input() placeholder = 'Escriba para buscar...';
  @Input() limite = 40;
  busqueda = '';
  abierto = false;
  /**
   * F57: ademas de por setDisabledState (que usa Angular Forms), se puede
   * desactivar desde la plantilla. La orden de compra lo necesita: hasta que
   * no hay proveedor no hay lista de productos que ofrecer, y un buscador
   * activo sobre una lista vacia solo invita a escribir en balde.
   */
  @Input() disabled = false;

  /**
   * Modo remoto: filtra la BASE, no el navegador.
   *
   * <p>Con `[remoto]="true"` el componente no filtra nada por su cuenta: emite
   * lo escrito por `(buscar)` con un respiro de 250 ms y pinta lo que el padre
   * le devuelva en `items`. Es obligatorio para cualquier catálogo que pueda
   * pasar de unos miles de filas — traerse la lista entera al navegador para
   * filtrarla ahí es lo que colgaba «Pedido nuevo» (F93).
   */
  @Input() remoto = false;
  @Output() buscar = new EventEmitter<string>();
  /** Lo pone el padre mientras la petición está en vuelo. */
  @Input() buscando = false;

  indiceActivo = -1;
  opcionesVisibles: any[] = [];
  totalFiltradas = 0;

  private onChange: (value: any) => void = () => {};
  private onTouched: () => void = () => {};
  private temporizador?: ReturnType<typeof setTimeout>;

  constructor(private elementRef: ElementRef<HTMLElement>) {}

  ngOnDestroy(): void {
    clearTimeout(this.temporizador);
  }

  /**
   * Red de seguridad para quien modifique el array EN SITIO.
   *
   * <p>El recálculo se dispara al asignar `items`, pero una pantalla que haga
   * `this.productos.push(...)` sin reasignar no pasa por el setter, y la lista
   * se quedaría congelada. Comparar dos longitudes en cada ciclo cuesta nada
   * —que es justo lo contrario de lo que costaba filtrar aquí— y evita que la
   * optimización rompa una pantalla que hoy funciona.
   */
  private largoVisto = -1;
  ngDoCheck(): void {
    if (this._items.length !== this.largoVisto) {
      this.largoVisto = this._items.length;
      this.recalcular();
    }
  }

  /**
   * Rehace la lista visible. Se llama al escribir y al llegar items nuevos.
   *
   * <p>En modo remoto NO se vuelve a filtrar: lo que llega ya viene filtrado
   * por la base, y volver a pasarle el filtro local escondería resultados
   * legítimos — por ejemplo una búsqueda por documento, que no está en la
   * etiqueta que se pinta.
   */
  private recalcular(): void {
    if (this.remoto) {
      this.totalFiltradas = this._items.length;
      this.opcionesVisibles = this._items.slice(0, this.limite);
      return;
    }
    const q = this.normalizar(this.busqueda);
    const filtradas = q
      ? this._items.filter(item => this.normalizar(this.etiqueta(item)).includes(q))
      : this._items;
    this.totalFiltradas = filtradas.length;
    this.opcionesVisibles = filtradas.slice(0, this.limite);
  }
  etiqueta(item: any): string {
    if (!item) return '';
    if (this.labelKey.includes(',')) return this.labelKey.split(',').map(k => item[k.trim()]).filter(Boolean).join(' ');
    return String(item[this.labelKey] ?? item);
  }
  /**
   * Lo último que se pidió al servidor. `null` = todavía no se ha pedido nada.
   *
   * <p>Es lo que decide si al abrir hay que volver a consultar, y arregla un
   * fallo que se veía al meter el SEGUNDO producto de un pedido: la condición
   * era «pide sólo si la lista está vacía», así que tras la primera búsqueda la
   * lista ya no estaba vacía y el desplegable se quedaba enseñando los
   * resultados de la búsqueda anterior — con la caja de texto en blanco, que es
   * lo que lo hacía desconcertante. Parecía que no cargaba el catálogo.
   */
  private ultimaPedida: string | null = null;

  abrir(): void {
    if (this.disabled) { return; }
    this.abierto = true;
    this.indiceActivo = -1;
    if (!this.remoto) { return; }
    // Se consulta si lo que hay en la caja no es lo que se pidió la última vez.
    // Con la caja vacía tras elegir algo, eso vuelve a traer la lista general;
    // reabrir sin tocar nada no gasta una consulta.
    const q = this.busqueda.trim();
    if (this.ultimaPedida !== q) { this.pedir(q); }
  }

  private pedir(q: string): void {
    this.ultimaPedida = q;
    this.buscar.emit(q);
  }
  alternar(): void { this.abierto ? this.cerrar() : this.abrir(); }

  alEscribir(): void {
    this.onChange(null);
    this.abierto = true;
    this.indiceActivo = -1;
    if (!this.remoto) { this.recalcular(); return; }
    // 250 ms de respiro: sin esto, «Rodríguez» son diez consultas a una tabla
    // de millón y medio de filas, y sólo importa la última.
    clearTimeout(this.temporizador);
    const q = this.busqueda.trim();
    this.temporizador = setTimeout(() => this.pedir(q), 250);
  }
  seleccionar(item: any): void {
    const valor = this.valueKey ? item[this.valueKey] : item;
    this.busqueda = this.etiqueta(item);
    // Al elegir ya no hay búsqueda pendiente: si quedara una en el temporizador,
    // llegaría después y reemplazaría la lista sin motivo.
    clearTimeout(this.temporizador);
    this.onChange(valor); this.onTouched(); this.cerrar();
  }
  alTeclado(event: KeyboardEvent): void {
    const opciones = this.opcionesVisibles;
    if (event.key === 'ArrowDown') { event.preventDefault(); this.indiceActivo = Math.min(this.indiceActivo + 1, opciones.length - 1); }
    else if (event.key === 'ArrowUp') { event.preventDefault(); this.indiceActivo = Math.max(this.indiceActivo - 1, 0); }
    else if (event.key === 'Enter' && this.indiceActivo >= 0) { event.preventDefault(); this.seleccionar(opciones[this.indiceActivo]); }
    else if (event.key === 'Escape') this.cerrar();
  }
  cerrar(): void { this.abierto = false; this.indiceActivo = -1; this.onTouched(); }
  /**
   * Pinta en la caja lo que corresponda al valor que llega de fuera.
   *
   * <p><b>Ojo con el borrado de lo tecleado (F77).</b> Cada letra llama a
   * {@link alEscribir}, que avisa al padre con `null` porque lo escrito a
   * medias todavia no es una eleccion. Si el modelo del padre no era ya nulo
   * —por ejemplo, si arranca como cadena vacia—, ese null lo CAMBIA, Angular
   * responde llamando aqui, y la busqueda se borraba: la lista volvia entera a
   * la primera letra y filtrar era imposible. Pasaba en el empaque y no en el
   * picking solo porque alli el modelo ya nacia nulo.
   *
   * <p>Por eso, mientras la lista esta abierta —es decir, mientras se esta
   * escribiendo— un valor sin correspondencia NO borra lo tecleado. Con la
   * lista cerrada si: ahi el nulo viene de fuera de verdad, y significa
   * limpiar.
   */
  writeValue(value: any): void {
    const item = this._items.find(opcion => this.valueKey ? opcion?.[this.valueKey] === value : opcion === value);
    if (item) { this.busqueda = this.etiqueta(item); this.recalcular(); return; }
    if (!this.abierto) { this.busqueda = ''; this.recalcular(); }
  }
  registerOnChange(fn: (value: any) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(disabled: boolean): void { this.disabled = disabled; }
  @HostListener('document:mousedown', ['$event'])
  clickFuera(event: MouseEvent): void { if (!this.elementRef.nativeElement.contains(event.target as Node)) this.cerrar(); }
  private normalizar(value: string): string { return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase().trim(); }
}
