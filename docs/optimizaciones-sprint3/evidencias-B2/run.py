#!/usr/bin/env python3
"""Run prebuilt instrumented APKs on ONE idle API23+ emulator; no backend required.
Usage: run.py SERIAL APK_DIRECTORY OUTPUT_DIRECTORY
APK_DIRECTORY: before.apk, before-test.apk, after.apk, after-test.apk.
"""
import hashlib,json,os,pathlib,subprocess,sys
serial,apks,out=sys.argv[1:]
apks=pathlib.Path(apks);out=pathlib.Path(out);out.mkdir(parents=True,exist_ok=True)
adb=os.environ.get('ADB',str(pathlib.Path.home()/'Android/Sdk/platform-tools/adb'))
def call(*args):
 return subprocess.check_output([adb,'-s',serial,*args],text=True,stderr=subprocess.STDOUT)
metadata={'serial':serial,'sdk':call('shell','getprop','ro.build.version.sdk').strip(),
 'model':call('shell','getprop','ro.product.model').strip(),'cpu_count':call('shell','getconf','_NPROCESSORS_ONLN').strip(),
 'meminfo':call('shell','cat','/proc/meminfo').splitlines()[0],
 'apk_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in apks.glob('*.apk')},
 'order':[['before','after'],['after','before'],['before','after']],
 'note':'Debuggable APK, temporary identical probes; synthetic component workloads, not end-to-end latency or physical-device profiling. Process allocation counter includes other app threads. No forced GC.'}
(out/'metadata.json').write_text(json.dumps(metadata,indent=2)+'\n')
for scale in ['window_animation_scale','transition_animation_scale','animator_duration_scale']:
 call('shell','settings','put','global',scale,'0')
for rep,order in enumerate(metadata['order'],1):
 for variant in order:
  for suffix in ['', '-test']:call('install','-r',str(apks/f'{variant}{suffix}.apk'))
  call('shell','am','force-stop','co.edu.uniandes.vinilos')
  call('shell','pm','clear','co.edu.uniandes.vinilos')
  log=call('shell','am','instrument','-w','-r','-e','b2Variant',variant,'-e','class',
   'co.edu.uniandes.vinilos.ui.artists.B2MeasurementTest',
   'co.edu.uniandes.vinilos.test/co.edu.uniandes.vinilos.testing.VinilosTestRunner')
  (out/f'{variant}-r{rep}.txt').write_text(log)
  if 'OK (1 test)' not in log:raise SystemExit(f'Failed: {variant} rep {rep}; inspect raw output')
  print(variant,rep,'OK',flush=True)
