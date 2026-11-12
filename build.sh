#!/bin/sh
# Compila y firma el APK sin Gradle: aapt2 → javac → D8 → apksig.
# Requiere en tools/: android.jar (API 36), aapt2 y jadx-all.jar (aporta D8 y apksig).
set -e
cd "$(dirname "$0")"
T=tools
KS=${KEYSTORE:-keystore/release.p12}
KS_PASS=${KEYSTORE_PASSWORD:-hogarlocal}
KS_ALIAS=${KEYSTORE_ALIAS:-hogarlocal}
OUT=out/HogarLocal.apk

rm -rf build/apk && mkdir -p build/apk/res build/apk/gen build/apk/classes build/apk/dex build/tools out
if [ ! -f "$KS" ]; then
    mkdir -p "$(dirname "$KS")"
    keytool -genkeypair -keystore "$KS" -storetype PKCS12 -storepass "$KS_PASS" -alias "$KS_ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Hogar Local" >/dev/null 2>&1
    echo "Clave nueva en $KS: consérvala para poder actualizar la app."
fi

$T/aapt2 compile --dir res -o build/apk/res/res.zip
$T/aapt2 link -o build/apk/base.apk -I $T/android.jar --manifest AndroidManifest.xml \
    --java build/apk/gen --min-sdk-version 26 --target-sdk-version 36 build/apk/res/res.zip
# android.jar no trae LambdaMetafactory: se compila contra la API de Java 8 del JDK y las clases de Android.
javac -encoding UTF-8 --release 8 -Xlint:-options -nowarn -cp $T/android.jar \
    -d build/apk/classes $(find src build/apk/gen -name '*.java')
java -cp $T/jadx-all.jar com.android.tools.r8.D8 --release --min-api 26 --lib $T/android.jar \
    --output build/apk/dex $(find build/apk/classes -name '*.class')
cp build/apk/base.apk build/apk/unsigned.apk
(cd build/apk/dex && zip -q -9 ../unsigned.apk classes.dex)
javac -nowarn -cp $T/jadx-all.jar -d build/tools $T/src/Sign.java $T/src/Verify.java
java -cp $T/jadx-all.jar:build/tools Sign build/apk/unsigned.apk "$OUT" "$KS" "$KS_ALIAS" "$KS_PASS"
java -cp $T/jadx-all.jar:build/tools Verify "$OUT"
(cd out && sha256sum HogarLocal.apk > SHA256SUMS.txt)
ls -l "$OUT"
