// Muestreador de memoria sin tocar el proceso de la app (Android 5.0 / API 21).
// Uso (WSL): node mem-sampler.mjs PID salida.csv archivo-de-parada [periodo_ms=500]
// Cada periodo lee /proc/uptime y, con run-as, /proc/PID/smaps; clasifica cada región como
// android_os_Debug.cpp de Android 5.0 y agrupa la memoria privada (Private_Dirty + Private_Clean)
// con las categorías de la «App Summary» que grafica Android Studio. No usa dumpsys meminfo,
// porque en API 21 fuerza recolecciones de basura en la app (Debug.countInstancesOfClass).
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';

const ADB = '/mnt/c/Users/yoima/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const PKG = 'co.edu.uniandes.vinilos';
const [pid, outFile, stopFile, periodArg] = process.argv.slice(2);
const period = Number(periodArg ?? 500);

function heapOf(name, start, prev) {
  if (name.startsWith('[heap]')) return 'native';
  if (name.startsWith('/dev/ashmem')) {
    if (name.startsWith('/dev/ashmem/dalvik-')) {
      if (/^\/dev\/ashmem\/dalvik-(alloc space|main space|large object space|free list large object space|non moving space|zygote space)/.test(name)) return 'dalvik';
      return 'dalvik_other';
    }
    if (name.startsWith('/dev/ashmem/CursorWindow')) return 'cursor';
    if (name.startsWith('/dev/ashmem/libc malloc')) return 'native';
    return 'ashmem';
  }
  if (name.startsWith('[anon:libc_malloc]')) return 'native';
  if (name.startsWith('[stack')) return 'stack';
  if (name.startsWith('/dev/')) return name.startsWith('/dev/kgsl-3d0') ? 'gl_dev' : 'other_dev';
  if (name.endsWith('.so')) return 'so';
  if (name.endsWith('.jar')) return 'jar';
  if (name.endsWith('.apk')) return 'apk';
  if (name.endsWith('.ttf')) return 'ttf';
  if (name.endsWith('.dex') || name.endsWith('.odex')) return 'dex';
  if (name.endsWith('.oat')) return 'oat';
  if (name.endsWith('.art')) return 'art';
  if (name.startsWith('[anon:')) return 'unknown';
  if (name.length > 0) return 'other_mmap';
  if (prev && prev.heap === 'so' && prev.end === start) return 'so'; // .bss de una biblioteca
  return 'unknown';
}

function parse(text) {
  const lines = text.split('\n');
  const uptime = Number(lines[0].split(' ')[0]);
  const heaps = {};
  let cur = null, prev = null, pss = 0;
  for (const line of lines.slice(1)) {
    const m = /^([0-9a-f]+)-([0-9a-f]+)\s+\S+\s+\S+\s+\S+\s+\S+\s*(.*)$/.exec(line);
    if (m) {
      const start = parseInt(m[1], 16), end = parseInt(m[2], 16);
      const heap = heapOf(m[3].trim(), start, prev);
      cur = { heap, end }; prev = cur;
      heaps[heap] ??= { pd: 0, pc: 0 };
      continue;
    }
    const f = /^(Pss|Private_Dirty|Private_Clean):\s+(\d+) kB/.exec(line);
    if (!f || !cur) continue;
    const v = Number(f[2]);
    if (f[1] === 'Pss') pss += v;
    else if (f[1] === 'Private_Dirty') heaps[cur.heap].pd += v;
    else heaps[cur.heap].pc += v;
  }
  const priv = (h) => (heaps[h] ? heaps[h].pd + heaps[h].pc : 0);
  const total = Object.keys(heaps).reduce((a, h) => a + priv(h), 0);
  const java = (heaps.dalvik?.pd ?? 0) + priv('art');
  const native = heaps.native?.pd ?? 0;
  const code = ['so', 'jar', 'apk', 'ttf', 'dex', 'oat'].reduce((a, h) => a + priv(h), 0);
  const stack = heaps.stack?.pd ?? 0;
  const graphics = priv('gl_dev');
  return { uptime, total, java, native, graphics, stack, code, others: total - java - native - code - stack - graphics, pss, heaps };
}

fs.writeFileSync(outFile, 'uptime_s,total_kb,java_kb,native_kb,graphics_kb,stack_kb,code_kb,others_kb,pss_kb,read_ms\n');
let n = 0;
while (!fs.existsSync(stopFile)) {
  const t0 = Date.now();
  let text;
  try {
    text = execFileSync(ADB, ['exec-out', `cat /proc/uptime; run-as ${PKG} cat /proc/${pid}/smaps`], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  } catch (e) { fs.appendFileSync(outFile + '.err', `${new Date().toISOString()} ${e.message}\n`); continue; }
  const r = parse(text);
  const ms = Date.now() - t0;
  if (r.total > 0) {
    fs.appendFileSync(outFile, [r.uptime, r.total, r.java, r.native, r.graphics, r.stack, r.code, r.others, r.pss, ms].join(',') + '\n');
    if (n++ === 0) fs.writeFileSync(outFile.replace(/\.csv$/, '-regiones.json'), JSON.stringify(r.heaps, null, 2));
  }
  const wait = period - (Date.now() - t0);
  if (wait > 0) Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, wait);
}
