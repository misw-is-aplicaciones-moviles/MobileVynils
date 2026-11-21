#!/usr/bin/env python3
"""Parse original instrumentation output; report median and range without dropping regressions."""
import json,pathlib,statistics,sys
root=pathlib.Path(sys.argv[1]);data={}
for variant in ['before','after']:
 data[variant]={}
 for rep in range(1,4):
  log=(root/f'{variant}-r{rep}.txt').read_text()
  assert 'OK (1 test)' in log
  for line in log.splitlines():
   if not line.startswith('B2PERF '):continue
   row=json.loads(line[7:]);scenario=row.pop('scenario')
   for metric,value in row.items():data[variant].setdefault(scenario,{}).setdefault(metric,[]).append(value)
summary={}
for scenario,metrics in data['before'].items():
 if scenario=='sink':continue
 summary[scenario]={}
 for metric,before in metrics.items():
  after=data['after'][scenario][metric]
  b,a=statistics.median(before),statistics.median(after)
  summary[scenario][metric]={'before':{'values':before,'median':b,'range':[min(before),max(before)]},
   'after':{'values':after,'median':a,'range':[min(after),max(after)]},'change_percent':(a-b)/b*100 if b else None}
(root/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
for scenario,metrics in summary.items():
 print(scenario)
 for metric,row in metrics.items():
  if metric=='operations':continue
  print(' ',metric,row['before']['median'],'->',row['after']['median'],f"({row['change_percent']:+.1f}%)" if row['change_percent'] is not None else '')
