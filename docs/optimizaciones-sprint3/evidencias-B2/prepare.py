#!/usr/bin/env python3
"""Install identical temporary probes/harness in an isolated worktree. Never run on the delivery tree."""
import pathlib,re,sys
root=pathlib.Path(sys.argv[1]).resolve()
source=pathlib.Path(__file__).resolve().parent
adapter=root/'app/src/main/java/co/edu/uniandes/vinilos/ui/artists/PerformerDetailAdapter.kt'
s=adapter.read_text()
# Counts actual execution sites, including holder construction (not source-line estimates).
s=s.replace('DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())', 'B2Probe.formatter()')
s=re.sub(r'(?m)^(\s*)(binding\.[\w.]+\.setOnClickListener)',r'\1B2Probe.listeners++\n\1\2',s)
s=s.replace('fun bind(row: PerformerDetailRow.Header) {', 'fun bind(row: PerformerDetailRow.Header) {\n            B2Probe.headerBinds++')
s=s.replace('fun bind(detail: PerformerDetail) {', 'fun bind(detail: PerformerDetail) {\n            B2Probe.headerBinds++')
adapter.write_text(s)
(adapter.parent/'B2Probe.kt').write_text('''package co.edu.uniandes.vinilos.ui.artists
import java.time.format.DateTimeFormatter
import java.util.Locale
internal object B2Probe {
 var headerBinds = 0
 var formatters = 0
 var listeners = 0
 fun reset() { headerBinds = 0; formatters = 0; listeners = 0 }
 fun formatter(): DateTimeFormatter { formatters++; return DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }
}
''')
target=root/'app/src/androidTest/java/co/edu/uniandes/vinilos/ui/artists/B2MeasurementTest.kt'
target.parent.mkdir(parents=True,exist_ok=True)
target.write_text((source/'B2MeasurementTest.kt.txt').read_text())
