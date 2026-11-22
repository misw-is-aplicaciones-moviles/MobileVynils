// Uso: node consolidar.mjs <carpeta-formal>
// Procesa cada pasada (procesar.mjs ya escribió resultados.json) y escribe, con el formato del aporte del P20:
// consolidado.json, resumen.csv, estabilidad-resumen.json, estabilidad-eventos.log y sha256-datos.json.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const root = process.argv[2];
const STAGES = ['antes', 'despues'];
const HUS = ['HU02', 'HU04', 'HU05', 'HU07', 'HU09', 'HU10', 'HU12', 'HU13', 'HU14', 'HU15'];
const median = (a) => { const s = [...a].sort((x, y) => x - y); return s[Math.floor(s.length / 2)]; };
const stat = (values) => ({ values, median: median(values), range: [Math.min(...values), Math.max(...values)] });

const passes = (st) => fs.readdirSync(path.join(root, st)).filter((d) => /^pasada-\d+$/.test(d)).sort();
const consolidado = {};
for (const st of STAGES) {
  const res = passes(st).map((p) => JSON.parse(fs.readFileSync(path.join(root, st, p, 'resultados.json'), 'utf8')));
  consolidado[st] = { complete_passes: res.length, stories: {} };
  for (const hu of HUS) {
    consolidado[st].stories[hu] = {
      memory_max_mb: stat(res.map((r) => r.stories[hu].memory_max_mb)),
      cpu_app_max_percent: stat(res.map((r) => r.stories[hu].cpu_app_max_percent)),
      window_duration_s: stat(res.map((r) => r.stories[hu].window_duration_s)),
      graphics_at_peak_mb: stat(res.map((r) => r.stories[hu].memory_categories_at_peak_mb.graphics)),
      java_at_peak_mb: stat(res.map((r) => r.stories[hu].memory_categories_at_peak_mb.java)),
      native_at_peak_mb: stat(res.map((r) => r.stories[hu].memory_categories_at_peak_mb.native)),
    };
  }
}
const f1 = (x) => x.toFixed(1);
const fmt = (s) => `${f1(s.median)} (${f1(s.range[0])}–${f1(s.range[1])})`;
const delta = (a, b) => { const d = (b - a) / a * 100; return `${d >= 0 ? '+' : ''}${d.toFixed(1)}%`; };
consolidado.deltas = {};
let csv = 'HU,Memoria antes mediana (rango) MB,Memoria después mediana (rango) MB,Delta memoria,CPU antes mediana (rango) %,CPU después mediana (rango) %,Delta CPU\n';
for (const hu of HUS) {
  const a = consolidado.antes.stories[hu], d = consolidado.despues.stories[hu];
  const dm = delta(a.memory_max_mb.median, d.memory_max_mb.median), dc = delta(a.cpu_app_max_percent.median, d.cpu_app_max_percent.median);
  consolidado.deltas[hu] = { memory: dm, cpu: dc,
    memory_ranges_overlap: !(d.memory_max_mb.range[1] < a.memory_max_mb.range[0] || d.memory_max_mb.range[0] > a.memory_max_mb.range[1]),
    cpu_ranges_overlap: !(d.cpu_app_max_percent.range[1] < a.cpu_app_max_percent.range[0] || d.cpu_app_max_percent.range[0] > a.cpu_app_max_percent.range[1]) };
  csv += [hu, fmt(a.memory_max_mb), fmt(d.memory_max_mb), dm, fmt(a.cpu_app_max_percent), fmt(d.cpu_app_max_percent), dc].join(',') + '\n';
}
fs.writeFileSync(path.join(root, 'consolidado.json'), JSON.stringify(consolidado, null, 2) + '\n');
fs.writeFileSync(path.join(root, 'resumen.csv'), csv);

// Estabilidad: Logcat del PID de la app y eventos am_anr / am_crash del paquete.
const est = { app_logs: {}, note: 'Counts are log messages for the app PID, not unique defects. StrictMode UntaggedSocketViolation does not exist on API 21 (added in API 26).' };
let eventos = '';
const sha = {};
for (const st of STAGES) for (const p of passes(st)) {
  const dir = path.join(root, st, p), key = `${st}-${p.replace('pasada-', '')}`;
  const pid = JSON.parse(fs.readFileSync(path.join(dir, 'entorno.json'), 'utf8')).pid;
  const lines = fs.readFileSync(path.join(dir, 'logcat-todo.txt'), 'utf8').split(/\r?\n/).filter((l) => new RegExp(`^\\S+ \\S+\\s+${pid}\\s`).test(l));
  const ev = fs.readFileSync(path.join(dir, 'logcat-eventos.txt'), 'utf8').split(/\r?\n/).filter((l) => /am_anr|am_crash/.test(l) && l.includes('co.edu.uniandes.vinilos'));
  eventos += `# ${key} (pid ${pid})\n${ev.join('\n')}${ev.length ? '\n' : ''}`;
  est.app_logs[key] = {
    pid,
    glide_load_failed: lines.filter((l) => /Load failed for/.test(l)).length,
    fatal: lines.filter((l) => /FATAL EXCEPTION/.test(l)).length,
    anr_or_crash_events: ev.length,
    explicit_gc: lines.filter((l) => /Explicit concurrent/.test(l)).length,
    skipped_frames_messages: lines.filter((l) => /Skipped \d+ frames/.test(l)).length,
    app_log_lines: lines.length,
  };
  for (const f of ['cpu-crudo.txt', 'memoria.csv', 'ventanas.json', 'logcat-todo.txt']) {
    sha[`${st}/${p}/${f}`] = crypto.createHash('sha256').update(fs.readFileSync(path.join(dir, f))).digest('hex');
  }
}
fs.writeFileSync(path.join(root, 'estabilidad-resumen.json'), JSON.stringify(est, null, 2) + '\n');
fs.writeFileSync(path.join(root, 'estabilidad-eventos.log'), eventos);
fs.writeFileSync(path.join(root, 'sha256-datos.json'), JSON.stringify(sha, null, 2) + '\n');
console.log(csv);
console.log(JSON.stringify(est.app_logs));
