// Uso: node procesar.mjs <carpeta-pasada>
// Lee cpu-crudo.txt, meminfo-crudo.txt y ventanas.json de una pasada del emulador API 21 y escribe
// HUxx-cpu.csv, HUxx-memory.csv y resultados.json con el mismo criterio del aporte del P20:
//  - Memoria Total = memoria privada (Private Dirty + Private Clean) de dumpsys meminfo, con las categorías
//    de la «App Summary» de Android (Debug.MemoryInfo.getSummary*), que es lo que grafica Studio. No es PSS.
//  - CPU App = Δ(utime+stime del proceso) / Δ(tiempo de CPU de todos los núcleos) × 100, limitado a 0–100.
import fs from 'node:fs';
import path from 'node:path';

const dir = process.argv[2];
const ventanas = JSON.parse(fs.readFileSync(path.join(dir, 'ventanas.json'), 'utf8'));

// ---------- CPU ----------
const cpuRaw = [];
for (const line of fs.readFileSync(path.join(dir, 'cpu-crudo.txt'), 'utf8').split(/\r?\n/)) {
  const [up, stat, cpu] = line.split('|');
  if (!cpu || !stat.includes(')')) continue;
  const t = stat.slice(stat.lastIndexOf(')') + 2).trim().split(/\s+/);
  const app = Number(t[11]) + Number(t[12]);
  const c = cpu.trim().split(/\s+/).slice(1, 9).map(Number);
  if (c.length < 8 || c.some(Number.isNaN) || Number.isNaN(app)) continue;
  const total = c.reduce((a, b) => a + b, 0);
  const idle = c[3] + c[4];
  cpuRaw.push({ ts: Number(up), app, total, busy: total - idle });
}
const clamp = (x) => Math.min(100, Math.max(0, x));
const cpu = [];
for (let i = 1; i < cpuRaw.length; i++) {
  const a = cpuRaw[i - 1], b = cpuRaw[i];
  const el = b.total - a.total;
  if (el <= 0) continue;
  const app = clamp(100 * (b.app - a.app) / el);
  const busy = clamp(100 * (b.busy - a.busy) / el);
  cpu.push({ ts: b.ts, app, studio: Math.trunc(app), others: clamp(busy - app) });
}

// ---------- Memoria ----------
// memoria.csv lo escribe mem-sampler.mjs (smaps leído con run-as, fuera del proceso de la app).
const mem = fs.readFileSync(path.join(dir, "memoria.csv"), "utf8").trim().split(/\r?\n/).slice(1).map((l) => {
  const [ts, total, java, native, graphics, stack, code, others, pss] = l.split(",").map(Number);
  return { ts, total, java, native, graphics, stack, code, others, pss };
});

// ---------- Por historia ----------
const MB = (kb) => kb / 1024;
const stories = {};
for (const [hu, w] of Object.entries(ventanas)) {
  const ms = mem.filter((x) => x.ts >= w.start_s && x.ts <= w.end_s);
  const cs = cpu.filter((x) => x.ts >= w.start_s && x.ts <= w.end_s);
  fs.writeFileSync(path.join(dir, `${hu}-memory.csv`), 'timestamp_s,total_kb,java_kb,native_kb,graphics_kb,stack_kb,code_kb,others_kb,pss_total_kb\n' +
    ms.map((x) => [x.ts.toFixed(3), x.total, x.java, x.native, x.graphics, x.stack, x.code, x.others, x.pss].join(',')).join('\n') + '\n');
  fs.writeFileSync(path.join(dir, `${hu}-cpu.csv`), 'timestamp_s,app_percent,studio_app_percent,others_percent\n' +
    cs.map((x) => [x.ts.toFixed(2), x.app, x.studio, x.others].join(',')).join('\n') + '\n');
  const pm = ms.reduce((a, x) => (!a || x.total > a.total ? x : a), null);
  const pc = cs.reduce((a, x) => (!a || x.app > a.app ? x : a), null);
  const gaps = cs.slice(1).map((x, i) => x.ts - cs[i].ts);
  stories[hu] = {
    memory_max_mb: MB(pm.total), cpu_app_max_percent: pc.app, cpu_app_max_studio_integer_percent: pc.studio,
    memory_initial_mb: MB(ms[0].total), memory_final_mb: MB(ms.at(-1).total),
    memory_categories_at_peak_mb: { total: MB(pm.total), java: MB(pm.java), native: MB(pm.native), graphics: MB(pm.graphics), stack: MB(pm.stack), code: MB(pm.code), others: MB(pm.others) },
    pss_at_peak_mb: MB(pm.pss),
    memory_peak_timestamp_s: pm.ts, cpu_peak_timestamp_s: pc.ts,
    memory_samples: ms.length, cpu_samples: cs.length, cpu_max_gap_s: Math.max(...gaps),
    window_start_s: w.start_s, window_end_s: w.end_s, window_duration_s: w.end_s - w.start_s, body_actions: w.body_actions, e2e_ms: null,
  };
}
const out = {
  measurement: 'Emulator API 21 via adb. Memory: /proc/PID/smaps read with run-as outside the app process every ~0.5 s (dumpsys meminfo is not used: on API 21 it forces GCs in the app), private memory (Private_Dirty + Private_Clean) classified like android_os_Debug.cpp of Android 5.0 and grouped like Android App Summary / Studio (Java = Dalvik Heap private dirty + .art; Native = Native Heap private dirty; Code = .so/.jar/.apk/.ttf/.dex/.oat; Stack; Graphics = Gfx dev + EGL/GL mtrack; Others = rest); Total = sum, not PSS (PSS kept for reference). CPU App: process utime+stime delta over all-core CPU time delta from /proc, ~0.2 s samples, clamped 0-100; integer value truncated like Studio.',
  stories,
};
fs.writeFileSync(path.join(dir, 'resultados.json'), JSON.stringify(out, null, 2) + '\n');
console.log(Object.entries(stories).map(([h, s]) => `${h} mem ${s.memory_max_mb.toFixed(1)} MB  cpu ${s.cpu_app_max_percent.toFixed(1)} %  dur ${s.window_duration_s.toFixed(1)} s  n=${s.memory_samples}/${s.cpu_samples} gap ${s.cpu_max_gap_s.toFixed(2)}`).join('\n'));
