// Uso (WSL, con @resvg/resvg-js en /tmp/pfcharts): node graficas.mjs <carpeta-formal>
// Escribe graficas/memoria.png, graficas/cpu.png, graficas/cambio-relativo.png y, por pasada y HU, HUxx/serie.png.
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
const require = createRequire('/tmp/pfcharts/');
const { Resvg } = require('@resvg/resvg-js');

const root = process.argv[2];
const HUS = ['HU02', 'HU04', 'HU05', 'HU07', 'HU09', 'HU10', 'HU12', 'HU13', 'HU14', 'HU15'];
const C = { surface: '#fcfcfb', ink: '#0b0b0b', ink2: '#52514e', grid: '#e4e3df', axis: '#b9b8b2', antes: '#2a78d6', despues: '#eb6834', down: '#2a78d6', up: '#e34948' };
const FONT = { fontFiles: ['/mnt/c/Windows/Fonts/segoeui.ttf', '/mnt/c/Windows/Fonts/segoeuib.ttf'], loadSystemFonts: false, defaultFontFamily: 'Segoe UI' };
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');
const t = (x, y, s, o = {}) => `<text x="${x}" y="${y}" font-size="${o.size ?? 13}" fill="${o.fill ?? C.ink2}" text-anchor="${o.anchor ?? 'start'}" font-weight="${o.weight ?? 400}">${esc(s)}</text>`;
const png = (svg, file, width) => { fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, new Resvg(svg, { font: FONT, fitTo: { mode: 'width', value: width * 2 } }).render().asPng()); };
const svgDoc = (w, h, body) => `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}"><rect width="${w}" height="${h}" fill="${C.surface}"/>${body}</svg>`;
const ticks = (lo, hi, n) => { const step = (hi - lo) / n; return Array.from({ length: n + 1 }, (_, i) => lo + i * step); };

const cons = JSON.parse(fs.readFileSync(path.join(root, 'consolidado.json'), 'utf8'));

// ---------- Mediana y rango por HU, antes y después (una métrica por gráfica, un solo eje) ----------
function rangeChart(metric, title, unit, lo, hi, file) {
  const W = 1100, H = 520, L = 70, R = 24, T = 92, B = 56, pw = W - L - R, ph = H - T - B;
  const y = (v) => T + ph - (v - lo) / (hi - lo) * ph, gw = pw / HUS.length;
  let b = t(L, 34, title, { size: 20, fill: C.ink, weight: 700 });
  b += t(L, 58, `Emulador API 21 · mediana (punto) y rango mínimo–máximo (barra) de los máximos de 3 pasadas por versión · ${unit}`, { size: 13 });
  // leyenda
  b += `<circle cx="${W - R - 250}" cy="${76}" r="6" fill="${C.antes}"/>` + t(W - R - 238, 81, 'Antes (6a8d5c1)', { fill: C.ink });
  b += `<circle cx="${W - R - 120}" cy="${76}" r="6" fill="${C.despues}"/>` + t(W - R - 108, 81, 'Después (e69c12b)', { fill: C.ink });
  for (const v of ticks(lo, hi, 4)) b += `<line x1="${L}" x2="${W - R}" y1="${y(v)}" y2="${y(v)}" stroke="${C.grid}" stroke-width="1"/>` + t(L - 10, y(v) + 4, v.toFixed(0), { anchor: 'end', size: 12 });
  HUS.forEach((hu, i) => {
    const cx = L + gw * i + gw / 2;
    for (const [k, dx, col] of [['antes', -14, C.antes], ['despues', 14, C.despues]]) {
      const s = cons[k].stories[hu][metric];
      b += `<line x1="${cx + dx}" x2="${cx + dx}" y1="${y(s.range[0])}" y2="${y(s.range[1])}" stroke="${col}" stroke-width="4" stroke-linecap="round" opacity="0.55"/>`;
      b += `<circle cx="${cx + dx}" cy="${y(s.median)}" r="6" fill="${col}" stroke="${C.surface}" stroke-width="2"/>`;
    }
    b += t(cx, H - B + 24, hu, { anchor: 'middle', fill: C.ink, size: 13 });
  });
  b += `<line x1="${L}" x2="${W - R}" y1="${T + ph}" y2="${T + ph}" stroke="${C.axis}"/>`;
  png(svgDoc(W, H, b), file, W);
}

// ---------- Cambio relativo de las medianas (divergente: azul baja, rojo sube) ----------
function deltaChart(file) {
  const W = 1100, H = 500, T = 96, B = 30, rowH = (H - T - B) / HUS.length;
  let b = t(40, 34, 'Cambio relativo de las medianas, después vs. antes', { size: 20, fill: C.ink, weight: 700 });
  b += t(40, 58, 'Emulador API 21 · (después − antes) / antes × 100 · azul: baja · rojo: sube', { size: 13 });
  const panels = [['Memoria Total', 'memory_max_mb', 150], ['CPU App', 'cpu_app_max_percent', 640]];
  const lim = 35;
  for (const [name, metric, x0] of panels) {
    const pw = 400, zx = x0 + pw / 2, sx = (v) => zx + v / lim * (pw / 2);
    b += t(zx, 86, name, { anchor: 'middle', fill: C.ink, size: 14, weight: 700 });
    for (const v of [-30, -15, 0, 15, 30]) b += `<line x1="${sx(v)}" x2="${sx(v)}" y1="${T - 4}" y2="${H - B}" stroke="${v === 0 ? C.axis : C.grid}" stroke-width="1"/>` + t(sx(v), H - B + 18, `${v > 0 ? '+' : ''}${v}%`, { anchor: 'middle', size: 11 });
    HUS.forEach((hu, i) => {
      const a = cons.antes.stories[hu][metric].median, d = cons.despues.stories[hu][metric].median, pct = (d - a) / a * 100;
      const cy = T + rowH * i + rowH / 2, x1 = Math.min(sx(0), sx(pct)), w = Math.max(Math.abs(sx(pct) - sx(0)), 1.5);
      b += `<rect x="${x1}" y="${cy - 10}" width="${w}" height="20" rx="4" fill="${pct < 0 ? C.down : C.up}"/>`;
      b += t(pct < 0 ? x1 - 6 : x1 + w + 6, cy + 4, `${pct >= 0 ? '+' : ''}${pct.toFixed(1)}%`, { anchor: pct < 0 ? 'end' : 'start', size: 12, fill: C.ink });
      if (x0 === 150) b += t(40, cy + 4, hu, { fill: C.ink, size: 13 });
    });
  }
  png(svgDoc(W, H, b), file, W);
}

// ---------- Serie temporal por pasada y HU: CPU App arriba, memoria Total abajo (dos paneles, no doble eje) ----------
function readCsv(f) { const [h, ...rows] = fs.readFileSync(f, 'utf8').trim().split(/\r?\n/); const k = h.split(','); return rows.map((r) => Object.fromEntries(r.split(',').map((v, i) => [k[i], Number(v)]))); }
function seriesChart(dir, stage, pass, hu, win) {
  const cpu = readCsv(path.join(dir, `${hu}-cpu.csv`)), mem = readCsv(path.join(dir, `${hu}-memory.csv`));
  const W = 900, H = 520, L = 64, R = 24, x0 = win.start_s, dur = win.end_s - win.start_s;
  const sx = (ts) => L + (ts - x0) / dur * (W - L - R);
  let b = t(L, 30, `${hu} · ${stage === 'antes' ? 'Antes (6a8d5c1)' : 'Después (e69c12b)'} · pasada ${pass}`, { size: 18, fill: C.ink, weight: 700 });
  b += t(L, 52, `Emulador API 21 · ventana de ${dur.toFixed(1)} s (${win.body_actions} acciones y 10 s de reposo) · CPU cada 0.2 s, memoria cada 0.5 s`, { size: 12 });
  const panel = (rows, key, scale, top, h, title, unit, lo, hi, col) => {
    const y = (v) => top + h - (v - lo) / (hi - lo) * h;
    let s = t(L, top - 10, title, { size: 13, fill: C.ink, weight: 700 });
    for (const v of ticks(lo, hi, unit === 'MB' ? Math.round((hi - lo) / 10) : 4)) s += `<line x1="${L}" x2="${W - R}" y1="${y(v)}" y2="${y(v)}" stroke="${C.grid}"/>` + t(L - 8, y(v) + 4, v.toFixed(0), { anchor: 'end', size: 11 });
    s += `<polyline fill="none" stroke="${col}" stroke-width="2" stroke-linejoin="round" points="${rows.map((r) => `${sx(r.ts).toFixed(1)},${y(r.v).toFixed(1)}`).join(' ')}"/>`;
    const pk = rows.reduce((a, r) => (r.v > a.v ? r : a), rows[0]);
    s += `<circle cx="${sx(pk.ts)}" cy="${y(pk.v)}" r="5" fill="${col}" stroke="${C.surface}" stroke-width="2"/>`;
    s += t(Math.min(sx(pk.ts) + 8, W - R - 120), Math.max(y(pk.v) - 8, top + 12), `máx. ${pk.v.toFixed(1)} ${unit}`, { size: 12, fill: C.ink });
    return s;
  };
  const c = cpu.map((r) => ({ ts: r.timestamp_s, v: r.app_percent })), m = mem.map((r) => ({ ts: r.timestamp_s, v: r.total_kb / 1024 }));
  const mlo = Math.floor(Math.min(...m.map((r) => r.v)) / 10) * 10 - 10, mhi = Math.ceil(Math.max(...m.map((r) => r.v)) / 10) * 10 + 10;
  const col = stage === 'antes' ? C.antes : C.despues;
  b += panel(c, 'v', 1, 96, 150, 'CPU App (%)', '%', 0, 100, col);
  b += panel(m, 'v', 1, 300, 150, 'Memoria Total, privada (MB)', 'MB', mlo, mhi, col);
  for (const s of ticks(0, dur, 6)) b += t(sx(x0 + s), H - 30, `${s.toFixed(0)} s`, { anchor: 'middle', size: 11 });
  b += t((W + L) / 2, H - 10, 'segundos desde el inicio de la ventana', { anchor: 'middle', size: 11 });
  png(svgDoc(W, H, b), path.join(dir, hu, 'serie.png'), W);
}

rangeChart('memory_max_mb', 'Memoria Total máxima por historia', 'MB', 60, 100, path.join(root, 'graficas', 'memoria.png'));
rangeChart('cpu_app_max_percent', 'CPU App máxima por historia', '%', 0, 80, path.join(root, 'graficas', 'cpu.png'));
deltaChart(path.join(root, 'graficas', 'cambio-relativo.png'));
let n = 0;
for (const st of ['antes', 'despues']) for (const p of fs.readdirSync(path.join(root, st)).filter((d) => /^pasada-\d+$/.test(d))) {
  const dir = path.join(root, st, p), win = JSON.parse(fs.readFileSync(path.join(dir, 'ventanas.json'), 'utf8'));
  for (const hu of HUS) { seriesChart(dir, st, p.replace('pasada-', ''), hu, win[hu]); n++; }
}
console.log(`ok: 3 gráficas de resumen y ${n} series`);
