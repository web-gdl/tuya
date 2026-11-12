#!/bin/sh
# Auditoría del APK: permisos, componentes, destinos de red y alineación.
set -e
cd "$(dirname "$0")"
APK=out/HogarLocal.apk
fail=0
perms=$(tools/aapt2 dump permissions $APK | grep "uses-permission" | sed "s/.*name='\(.*\)'/\1/" | sort | tr '\n' ' ')
echo "Permisos: $perms"
[ "$perms" = "android.permission.CHANGE_WIFI_MULTICAST_STATE android.permission.INTERNET " ] || { echo "FALLA: permisos inesperados"; fail=1; }
tools/aapt2 dump xmltree $APK --file AndroidManifest.xml > build/manifest.txt
for tag in service receiver provider; do
    if grep -q "E: $tag " build/manifest.txt; then echo "FALLA: hay <$tag>"; fail=1; fi
done
echo "Sin servicios, receptores ni proveedores: nada corre en segundo plano."
rm -rf build/audit && mkdir -p build/audit && unzip -q -o $APK -d build/audit
echo "Archivos: $(unzip -Z1 $APK | wc -l); bibliotecas nativas: $(unzip -Z1 $APK | grep -c '\.so$' || true)"
hosts=$(strings -n 6 build/audit/classes.dex | grep -oE 'https?://[A-Za-z0-9./_-]+' | sort -u | tr '\n' ' ')
echo "Destinos de internet en el código: $hosts"
for h in $hosts; do
    case "$h" in
        https://openapi*.tuyaus.com|https://openapi*.tuyaeu.com|https://openapi.tuyain.com|https://openapi.tuyacn.com|https://openapi-sg.iotbing.com) ;;
        *) echo "FALLA: destino inesperado $h"; fail=1 ;;
    esac
done
if strings -n 5 build/audit/classes.dex | grep -iE 'firebase|analytics|crashlytics|adservice|admob|facebook|appsflyer|umeng|bugly|sentry' ; then
    echo "FALLA: cadenas de rastreo"; fail=1
else
    echo "Sin SDK de estadísticas, anuncios ni informes de fallos."
fi
python3 - "$APK" <<'PY'
import sys, zipfile, struct
z = zipfile.ZipFile(sys.argv[1])
ok = True
for i in z.infolist():
    if i.filename == 'resources.arsc':
        with open(sys.argv[1], 'rb') as f:
            f.seek(i.header_offset + 26)
            n, m = struct.unpack('<HH', f.read(4))
            data = i.header_offset + 30 + n + m
        ok = i.compress_type == 0 and data % 4 == 0
        print('resources.arsc sin comprimir y alineado:', ok)
sys.exit(0 if ok else 1)
PY
[ $fail = 0 ] && echo "Auditoría correcta" || { echo "Auditoría con fallos"; exit 1; }
