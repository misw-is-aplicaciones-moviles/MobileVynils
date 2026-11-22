// Arma la carpeta de evidencias del emulador API 21 con la estructura del aporte del P20.
// Uso (WSL): node entrega.mjs <carpeta-formal> <carpeta-destino>
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
const require = createRequire('/tmp/pfcharts/');
const { Resvg } = require('@resvg/resvg-js');

const [src, dst] = process.argv.slice(2);
const HUS = ['HU02', 'HU04', 'HU05', 'HU07', 'HU09', 'HU10', 'HU12', 'HU13', 'HU14', 'HU15'];
fs.rmSync(dst, { recursive: true, force: true });
const cp = (a, b) => { fs.mkdirSync(path.dirname(b), { recursive: true }); fs.copyFileSync(a, b); };

// Captura del emulador reducida a 540 px de ancho (la original es 1080 × 2280).
function half(pngIn, pngOut) {
  const b64 = fs.readFileSync(pngIn).toString('base64');
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="540" height="1140"><image width="540" height="1140" xlink:href="data:image/png;base64,${b64}"/></svg>`;
  fs.mkdirSync(path.dirname(pngOut), { recursive: true });
  fs.writeFileSync(pngOut, new Resvg(svg, { fitTo: { mode: 'width', value: 540 } }).render().asPng());
}

for (const st of ['antes', 'despues']) for (const p of [1, 2, 3]) {
  const a = path.join(src, st, `pasada-${p}`), b = path.join(dst, st, `pasada-${p}`);
  for (const f of ['ventanas.json', 'entorno.json', 'resultados.json', 'cpu-crudo.txt', 'memoria.csv', 'memoria-regiones.json', 'acciones.log', 'fixtures.json', 'backend-final.json', 'logcat-eventos.txt']) cp(path.join(a, f), path.join(b, f));
  const pid = JSON.parse(fs.readFileSync(path.join(a, 'entorno.json'), 'utf8')).pid;
  const app = fs.readFileSync(path.join(a, 'logcat-todo.txt'), 'utf8').split(/\r?\n/).filter((l) => new RegExp(`^\\S+ \\S+\\s+${pid}\\s`).test(l));
  fs.writeFileSync(path.join(b, 'logcat-app.txt'), app.join('\n') + '\n');
  const checks = Object.fromEntries(fs.readFileSync(path.join(a, 'checks.txt'), 'utf8').trim().split(/\r?\n/).map((l) => { const m = /"(HU\d+)": (true|false)/.exec(l); return [m[1], m[2] === 'true']; }));
  fs.writeFileSync(path.join(b, 'completa.json'), JSON.stringify({ complete: Object.values(checks).every(Boolean), pilot: false, stories: Object.keys(checks).length, backend_checks: checks }, null, 2) + '\n');
  for (const hu of HUS) {
    cp(path.join(a, `${hu}-cpu.csv`), path.join(b, `${hu}-cpu.csv`));
    cp(path.join(a, `${hu}-memory.csv`), path.join(b, `${hu}-memory.csv`));
    cp(path.join(a, hu, 'serie.png'), path.join(b, hu, 'serie.png'));
    half(path.join(a, hu, 'emulador.png'), path.join(b, hu, 'emulador.png'));
  }
}
for (const f of ['consolidado.json', 'resumen.csv', 'estabilidad-resumen.json', 'estabilidad-eventos.log', 'sha256-datos.json']) cp(path.join(src, f), path.join(dst, f));
for (const f of fs.readdirSync(path.join(src, 'graficas'))) cp(path.join(src, 'graficas', f), path.join(dst, 'graficas', f));
const tools = '/mnt/c/WSL/pf-api21/run';
for (const f of ['lib.sh', 'fixtures.sh', 'pass.sh', 'serie.sh', 'sampler.sh', 'mem-sampler.mjs', 'procesar.mjs', 'consolidar.mjs', 'graficas.mjs', 'entrega.mjs']) cp(path.join(tools, f), path.join(dst, 'herramientas', f));
console.log('ok');
