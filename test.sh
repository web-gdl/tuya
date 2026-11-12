#!/bin/sh
# Pruebas en la JVM (sin Android): protocolo, descubrimiento, nube simulada y modelo.
set -e
cd "$(dirname "$0")"
rm -rf build/jvm && mkdir -p build/jvm
[ -f test/vectors.txt ] || python3 test/gen_vectors.py
javac -encoding UTF-8 -nowarn -d build/jvm $(find src/app/hogarlocal/proto src/app/hogarlocal/model -name '*.java') test/FakeDevice.java test/Tests.java
java -Dstdout.encoding=UTF-8 -cp build/jvm Tests
