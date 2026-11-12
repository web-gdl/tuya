# Historial de cambios

## 0.1.0 — 11-nov-2026

Primera versión.

- Protocolo local Tuya 3.1, 3.3, 3.4 y 3.5: tramas 55AA (CRC32 / HMAC-SHA256) y 6699 (AES-GCM), negociación de clave de sesión, modo «device22», latido cada 9 s y reconexión automática.
- Detección automática de versión e IP a partir de los anuncios UDP (puertos 6666 y 6667).
- Subdispositivos Zigbee a través de su hub (experimental).
- Inicio con hogar, cuartos y tarjetas; detalle con encendido, todo on/off, temporizador, consumo y nombre por tecla; pantalla para agregar.
- Claves locales: importar `devices.json` de tinytuya o respaldo propio, descargar de Tuya IoT, o a mano.
- Exportar respaldo en JSON. Tema claro y oscuro.
- 2 permisos, sin servicios ni receptores, sin SDK de terceros. APK de 75 KB.
