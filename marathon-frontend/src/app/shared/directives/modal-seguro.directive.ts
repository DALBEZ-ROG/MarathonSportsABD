import {
  AfterViewInit, Directive, ElementRef, EventEmitter, HostListener, Input, OnDestroy, Output
} from '@angular/core';

/**
 * Hace que un modal no se lleve por delante lo que el usuario estaba escribiendo (D4).
 *
 * **El problema.** Las 14 pantallas con formulario emergente tenían esto:
 *
 * ```html
 * <div class="modal-overlay" *ngIf="showModal" (click)="cerrarModal()">
 * ```
 *
 * Un clic fuera del cuadro —o un clic que se escapa un par de píxeles al
 * arrastrar para seleccionar texto— cerraba el modal y borraba el formulario
 * entero sin avisar. No había forma de recuperarlo.
 *
 * **Qué hace esta directiva.** Se pone en el mismo `div` y sustituye a aquel
 * `(click)`:
 *
 * ```html
 * <div class="modal-overlay" *ngIf="showModal" appModalSeguro (cerrar)="cerrarModal()">
 * ```
 *
 * - **Pulsar fuera no cierra.** Se cierra con Cancelar, con la ✕ o con Escape.
 * - **Escape pregunta si hay algo escrito.** Si el formulario está igual que
 *   cuando se abrió, cierra directamente; si se ha tocado algo, pide
 *   confirmación antes de perderlo.
 * - **El foco se queda dentro.** Tabulando no se sale del modal al fondo de la
 *   página, que es donde se perdía antes con el teclado.
 * - **El foco vuelve a donde estaba** al cerrar, en vez de saltar al principio
 *   del documento.
 * - Marca el cuadro como `role="dialog"` con `aria-modal`.
 *
 * Lo «sucio» se detecta comparando los campos con lo que había al abrir, así
 * que funciona igual en un alta —campos vacíos— que en una edición —campos ya
 * rellenos—: lo que cuenta es si el usuario ha cambiado algo.
 */
@Directive({
  selector: '[appModalSeguro]',
  standalone: true
})
export class ModalSeguroDirective implements AfterViewInit, OnDestroy {

  /**
   * Ponlo a `false` en modales que solo confirman o muestran datos: ahí no hay
   * nada que perder y preguntar es un estorbo.
   */
  @Input() confirmarSiHayCambios = true;

  @Input() textoConfirmacion =
    'Hay datos sin guardar. ¿Seguro que quieres cerrar y perderlos?';

  /** Petición de cierre ya validada. Conéctalo al mismo método que el botón Cancelar. */
  @Output() cerrar = new EventEmitter<void>();

  private valoresAlAbrir: string[] = [];
  private focoPrevio: HTMLElement | null = null;
  private temporizador?: ReturnType<typeof setTimeout>;

  private static readonly ENFOCABLES = [
    'a[href]', 'button:not([disabled])', 'input:not([disabled])',
    'select:not([disabled])', 'textarea:not([disabled])', '[tabindex]:not([tabindex="-1"])'
  ].join(',');

  constructor(private host: ElementRef<HTMLElement>) {}

  ngAfterViewInit(): void {
    const el = this.host.nativeElement;

    el.setAttribute('role', 'dialog');
    el.setAttribute('aria-modal', 'true');

    this.focoPrevio = document.activeElement as HTMLElement | null;

    // El formulario se rellena con ngModel, que escribe después de este gancho.
    // Sin esta espera, una edición se leería como "vacío al abrir" y cualquier
    // modal con datos parecería sucio nada más abrirse.
    this.temporizador = setTimeout(() => {
      this.valoresAlAbrir = this.valoresActuales();
      this.enfocarPrimero();
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.temporizador);
    // Devolver el foco donde estaba: si no, al cerrar el modal el teclado
    // vuelve al principio del documento y hay que tabular media pantalla.
    this.focoPrevio?.focus?.();
  }

  /**
   * Traga el clic en el fondo. No cierra: existe solo para que el evento no
   * suba a nadie más y para dejar dicho, aquí, que este es el comportamiento
   * buscado y no un descuido.
   */
  @HostListener('click', ['$event'])
  alPulsarElFondo(evento: MouseEvent): void {
    if (evento.target === this.host.nativeElement) {
      evento.stopPropagation();
    }
  }

  @HostListener('keydown', ['$event'])
  alPulsarTecla(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      this.pedirCierre();
      return;
    }
    if (evento.key === 'Tab') {
      this.atraparTabulador(evento);
    }
  }

  /** Cierra, preguntando antes si hay cambios sin guardar. */
  pedirCierre(): void {
    if (this.confirmarSiHayCambios && this.hayCambios()
        && !window.confirm(this.textoConfirmacion)) {
      return;
    }
    this.cerrar.emit();
  }

  // ------------------------------------------------------------------

  private hayCambios(): boolean {
    const ahora = this.valoresActuales();
    return ahora.length !== this.valoresAlAbrir.length
        || ahora.some((v, i) => v !== this.valoresAlAbrir[i]);
  }

  private valoresActuales(): string[] {
    const campos = this.host.nativeElement.querySelectorAll<
      HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>('input, select, textarea');
    return Array.from(campos).map(c =>
      c instanceof HTMLInputElement && (c.type === 'checkbox' || c.type === 'radio')
        ? String(c.checked)
        : c.value);
  }

  private enfocables(): HTMLElement[] {
    return Array.from(
      this.host.nativeElement.querySelectorAll<HTMLElement>(ModalSeguroDirective.ENFOCABLES)
    ).filter(e => e.offsetParent !== null);
  }

  /**
   * Enfoca el primer campo del formulario, no el primer elemento enfocable:
   * ese suele ser la ✕ de cerrar, y arrancar con el foco en «cerrar» invita a
   * pulsar Intro y perder el formulario.
   */
  private enfocarPrimero(): void {
    const lista = this.enfocables();
    const campo = lista.find(e => /^(INPUT|SELECT|TEXTAREA)$/.test(e.tagName));
    (campo ?? lista[0])?.focus();
  }

  private atraparTabulador(evento: KeyboardEvent): void {
    const lista = this.enfocables();
    if (lista.length === 0) {
      return;
    }
    const primero = lista[0];
    const ultimo = lista[lista.length - 1];
    const activo = document.activeElement;

    if (evento.shiftKey && activo === primero) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && activo === ultimo) {
      evento.preventDefault();
      primero.focus();
    }
  }
}
