# Muestreador de CPU en el dispositivo (mksh de Android 5.0). Uso: sh sampler.sh PID
# Mientras exista /data/local/tmp/muestreo, cada 0.2 s guarda /proc/uptime, /proc/PID/stat y la línea cpu de /proc/stat.
# Solo usa lecturas con el builtin read: no crea procesos salvo sleep y no toca la app.
PID=$1; D=/data/local/tmp
rm -f $D/cpu.txt
while [ -e $D/muestreo ]; do
  { read u x < /proc/uptime; read s < /proc/$PID/stat; read c < /proc/stat; echo "$u|$s|$c"; } >> $D/cpu.txt
  sleep 0.2
done
