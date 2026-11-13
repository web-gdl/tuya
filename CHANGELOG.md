# Historial de cambios

## 0.2.0 — 13-nov-2026

**Programación semanal de encendido y apagado.**

- Horarios por dispositivo: hora, acción (encender/apagar), días de la semana y teclas. Atajos *Todos*, *Lun–Vie* y *Sáb–Dom*.
- Pantalla *Programación* por dispositivo (desde su detalle) y de todo el hogar (*Ajustes → Programación*), con interruptor para activar o desactivar cada horario, próxima ejecución y resultado de la última.
- Los cumple el teléfono, sin nube: una sola alarma exacta para el próximo horario; a esa hora se conecta, manda la orden, espera la confirmación y se desconecta. Usa la conexión ya abierta si la app está en pantalla.
- Se reponen solas al reiniciar el teléfono, cambiar la hora o actualizar la app. No se mandan si el teléfono despierta con más de 30 min de retraso.
- Avisos en pantalla si faltan las alarmas exactas o si el ahorro de batería puede retrasarlas.
- Los horarios van en el respaldo y se borran con su dispositivo.
- Permisos nuevos: `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` (solo Android 12) y `RECEIVE_BOOT_COMPLETED`. Un receptor no exportado.
- 58 pruebas nuevas (cálculo de horarios con cambio de horario, guardado y respaldo, ejecución contra el dispositivo simulado y un hub).
- Guía: [docs/PROGRAMACION.md](docs/PROGRAMACION.md).

## 0.1.0 — 11-nov-2026

Primera versión.

- Protocolo local Tuya 3.1, 3.3, 3.4 y 3.5: tramas 55AA (CRC32 / HMAC-SHA256) y 6699 (AES-GCM), negociación de clave de sesión, modo «device22», latido cada 9 s y reconexión automática.
- Detección automática de versión e IP a partir de los anuncios UDP (puertos 6666 y 6667).
- Subdispositivos Zigbee a través de su hub (experimental).
- Inicio con hogar, cuartos y tarjetas; detalle con encendido, todo on/off, temporizador, consumo y nombre por tecla; pantalla para agregar.
- Claves locales: importar `devices.json` de tinytuya o respaldo propio, descargar de Tuya IoT, o a mano.
- Exportar respaldo en JSON. Tema claro y oscuro.
- 2 permisos, sin servicios ni receptores, sin SDK de terceros. APK de 75 KB.
