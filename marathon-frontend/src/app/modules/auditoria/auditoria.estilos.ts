/**
 * Los estilos que comparten las cuatro pestanas de Auditoria (F92).
 *
 * Nacieron dentro de auditoria.component.ts y se sacan aqui al partir la
 * pantalla en componentes. No es una libreria de estilos ni pretende serlo: es
 * el mismo bloque de siempre, en un sitio donde los tres componentes pueden
 * apuntarlo. Angular encapsula los estilos por componente, asi que la
 * alternativa era copiar 300 lineas tres veces y que se fueran separando.
 */
export const ESTILOS_AUDITORIA = `
    /* ── Qué es cada pestaña (F81) ─────────────────────────────── */
    .audit-que { margin: 0 0 1.1rem; padding: .7rem 1rem; font-size: .84rem;
                 line-height: 1.6; color: rgba(255,255,255,0.45);
                 background: rgba(255,255,255,0.02);
                 border-left: 2px solid var(--ms-gold);
                 border-radius: 0 8px 8px 0; max-width: 92ch; }
    .audit-que strong { color: rgba(255,255,255,0.75); }

    .filter-field.ancho { min-width: 300px; flex: 1 1 300px; }
    .filter-field:has(.search-select.open) { position: relative; z-index: 60; }

    /* F89 - la lista del buscador salia POR DETRAS de la tabla.
       ────────────────────────────────────────────────────────────────────
       Y el z-index de arriba no bastaba, que es lo que despistaba: era
       correcto, pero inutil. El bloque .audit-filters lleva backdrop-filter,
       y cualquier valor distinto de "none" CREA UN CONTEXTO DE APILAMIENTO
       PROPIO. Dentro de el, el z-index 60 del campo solo compite con sus
       hermanos del propio bloque de filtros; frente a la tabla, que esta
       fuera, no puede nada. Lo que decide entonces es el orden del DOM, y la
       tabla va despues.

       Asi que hay que levantar el CONTENEDOR entero, no el campo. Solo
       mientras hay un desplegable abierto, para no dejar los filtros por
       encima de nada el resto del tiempo.

       Regla general, que volvera a hacer falta: si un desplegable se esconde
       detras de algo, mira si algun ancestro tiene backdrop-filter, filter,
       transform u opacity — los cuatro crean contexto de apilamiento, y ahi
       se queda encerrado el z-index. */
    .audit-filters:has(.search-select.open) { position: relative; z-index: 60; }

    .atajos { display: flex; align-items: center; gap: .4rem; flex-wrap: wrap;
              margin: .9rem 0 .2rem; }
    .atajos > span { font-size: .76rem; color: rgba(255,255,255,0.3); margin-right: .2rem; }
    .atajos button { background: rgba(255,255,255,0.03);
                     border: 1px solid rgba(255,255,255,0.08);
                     color: rgba(255,255,255,0.5); padding: .3rem .75rem;
                     border-radius: 99px; font-size: .76rem; cursor: pointer;
                     font-family: inherit; }
    .atajos button:hover { border-color: #C9A84C; color: #C9A84C; }

    .cuantos { margin: .8rem 0 .6rem; font-size: .85rem; color: rgba(255,255,255,0.4); }
    .cuantos strong { color: #F4E28D; font-size: 1.05rem; }

    /* Aprobar, anular, reembolsar y liberar mueven dinero o stock: en una lista
       de cientos de líneas iguales, son las que hay que poder encontrar. */
    .accion-chip.sensible { background: rgba(217,119,6,.16); color: #fcd34d;
                            border-color: rgba(217,119,6,.4); }

    .audit-page {
      max-width: 1300px;
      padding: 2rem;
      margin: 0 auto;
    }

    .audit-header {
      margin-bottom: 2rem;
    }

    .audit-header h1 {
      font-size: 1.8rem;
      font-weight: 400;
      letter-spacing: 1px;
      color: #fff;
      margin-bottom: 0.3rem;
    }

    .audit-subtitle {
      color: rgba(255,255,255,0.35);
      font-size: 0.85rem;
      font-weight: 300;
    }

    /* ── Filters ── */
    .audit-filters {
      display: flex;
      gap: 1rem;
      align-items: flex-end;
      flex-wrap: wrap;
      padding: 1.25rem;
      background: rgba(255,255,255,0.02);
      border: 1px solid rgba(255,255,255,0.06);
      border-radius: 14px;
      margin-bottom: 1.5rem;
      backdrop-filter: blur(8px);
    }

    .filter-field {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
      min-width: 140px;
    }

    .filter-field label {
      font-size: 0.68rem;
      font-weight: 500;
      color: rgba(255,255,255,0.35);
      text-transform: uppercase;
      letter-spacing: 0.8px;
    }

    .filter-field input,
    .filter-field select {
      padding: 0.6rem 0.9rem;
      background: rgba(255,255,255,0.04);
      border: 1px solid rgba(255,255,255,0.08);
      border-radius: 8px;
      color: rgba(255,255,255,0.85);
      font-size: 0.84rem;
      outline: none;
      transition: all 0.3s;
    }

    .filter-field input:focus,
    .filter-field select:focus {
      border-color: rgba(201,168,76,0.4);
      background: rgba(255,255,255,0.06);
      box-shadow: 0 0 0 3px rgba(201,168,76,0.08);
    }

    .filter-field select option {
      background: #1a1a2e;
      color: #fff;
    }

    .filter-field input::placeholder {
      color: rgba(255,255,255,0.2);
    }

    .filter-btn-wrap {
      justify-content: flex-end;
    }

    .btn-search {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.6rem 1.2rem;
      background: linear-gradient(135deg, #C9A84C, #a08339);
      color: #0a0a0f;
      border: none;
      border-radius: 8px;
      cursor: pointer;
      font-weight: 600;
      font-size: 0.82rem;
      transition: all 0.3s;
      box-shadow: 0 4px 12px rgba(201,168,76,0.2);
    }

    .btn-search:hover {
      box-shadow: 0 6px 20px rgba(201,168,76,0.35);
      transform: translateY(-1px);
    }

    /* ── Table ── */
    .audit-table-wrap {
      overflow-x: auto;
      border-radius: 14px;
      border: 1px solid rgba(255,255,255,0.06);
      box-shadow: 0 4px 24px rgba(0,0,0,0.2);
    }

    .audit-table {
      width: 100%;
      border-collapse: collapse;
      background: rgba(255,255,255,0.02);
      backdrop-filter: blur(12px);
    }

    .audit-table th {
      background: rgba(255,255,255,0.03);
      color: rgba(255,255,255,0.45);
      padding: 0.85rem 1rem;
      text-align: left;
      font-size: 0.7rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      border-bottom: 1px solid rgba(255,255,255,0.06);
    }

    .audit-table td {
      padding: 0.75rem 1rem;
      border-bottom: 1px solid rgba(255,255,255,0.03);
      font-size: 0.84rem;
      color: rgba(255,255,255,0.7);
    }

    .audit-table tr:hover td {
      background: rgba(255,255,255,0.03);
    }

    .audit-table .num {
      text-align: right;
      font-variant-numeric: tabular-nums;
    }

    .fecha-col {
      color: rgba(255,255,255,0.4) !important;
      font-size: 0.8rem !important;
      white-space: nowrap;
    }

    .desc-col {
      max-width: 280px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .ip-col {
      font-family: 'Cascadia Code', 'Fira Code', monospace;
      font-size: 0.78rem !important;
      color: rgba(255,255,255,0.4) !important;
    }

    /* ── Diff Badge ── */
    .diff-badge {
      display: inline-block;
      padding: 0.2rem 0.6rem;
      border-radius: 6px;
      font-size: 0.78rem;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }

    .diff-up {
      background: rgba(76,175,80,0.12);
      color: #81C784;
    }

    .diff-down {
      background: rgba(229,115,115,0.12);
      color: #EF9A9A;
    }

    .diff-zero {
      background: rgba(255,255,255,0.04);
      color: rgba(255,255,255,0.3);
    }

    /* ── Motivo chip ── */
    .motivo-chip {
      display: inline-block;
      padding: 0.2rem 0.6rem;
      border-radius: 6px;
      font-size: 0.75rem;
      background: rgba(255,255,255,0.04);
      border: 1px solid rgba(255,255,255,0.08);
      color: rgba(255,255,255,0.6);
      text-transform: capitalize;
    }

    /* ── Modulo Badge ── */
    .modulo-badge {
      display: inline-block;
      padding: 0.22rem 0.65rem;
      border-radius: 6px;
      font-size: 0.72rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.4px;
      background: rgba(100,181,246,0.12);
      color: #90CAF9;
      border: 1px solid rgba(100,181,246,0.2);
    }

    .modulo-badge[data-modulo="auth"] {
      background: rgba(33,150,243,0.12);
      color: #64B5F6;
      border-color: rgba(33,150,243,0.25);
    }

    .modulo-badge[data-modulo="pedidos"] {
      background: rgba(76,175,80,0.12);
      color: #81C784;
      border-color: rgba(76,175,80,0.25);
    }

    .modulo-badge[data-modulo="empaque"] {
      background: rgba(255,152,0,0.12);
      color: #FFB74D;
      border-color: rgba(255,152,0,0.25);
    }

    .modulo-badge[data-modulo="usuarios"] {
      background: rgba(156,39,176,0.12);
      color: #CE93D8;
      border-color: rgba(156,39,176,0.25);
    }

    .modulo-badge[data-modulo="comprobantes"] {
      background: rgba(96,125,139,0.12);
      color: #90A4AE;
      border-color: rgba(96,125,139,0.25);
    }

    .modulo-badge[data-modulo="inventario"] {
      background: rgba(255,193,7,0.12);
      color: #FFD54F;
      border-color: rgba(255,193,7,0.25);
    }

    .modulo-badge[data-modulo="picking"] {
      background: rgba(201,168,76,0.12);
      color: #C9A84C;
      border-color: rgba(201,168,76,0.25);
    }

    /* ── Acción chip ── */
    .accion-chip {
      display: inline-block;
      padding: 0.2rem 0.6rem;
      border-radius: 6px;
      font-size: 0.75rem;
      background: rgba(201,168,76,0.08);
      color: rgba(201,168,76,0.8);
      border: 1px solid rgba(201,168,76,0.15);
      text-transform: capitalize;
    }

    @media (max-width: 768px) {
      .audit-filters {
        flex-direction: column;
        align-items: stretch;
      }
      .filter-field { min-width: 100%; }
      .desc-col { max-width: 150px; }
    }
`;
