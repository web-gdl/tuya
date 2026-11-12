# Protocolo local de Tuya (resumen de lo implementado)

Referencia técnica de lo que hace `src/app/hogarlocal/proto/`. Basado en la documentación pública de tinytuya y tuya-local.

## Transporte

- **Control:** TCP 6668, una conexión persistente por dispositivo (o por hub).
- **Descubrimiento:** los dispositivos difunden un anuncio UDP cada ~5 s.
  - 6666: versión 3.1, JSON sin cifrar.
  - 6667: 3.3 y posteriores, cifrado con AES-ECB y la clave `md5("yGAdlopoPVldABfn")` (3.5 usa GCM con la misma clave).
  - El anuncio trae `gwId`, `ip`, `version` y `productKey`. Hogar Local solo escucha; nunca envía nada por UDP.

## Tramas

**55AA** (3.1–3.4):

```
000055AA | secuencia | comando | longitud | [código de retorno] | datos | CRC32 o HMAC-SHA256 | 0000AA55
```

- 3.1–3.3: CRC32 (4 bytes). 3.4: HMAC-SHA256 (32 bytes) con la clave de sesión.
- Las respuestas del dispositivo llevan un código de retorno de 4 bytes delante de los datos.

**6699** (3.5):

```
00006699 | 0000 | secuencia | comando | longitud | IV (12) | datos cifrados AES-GCM | etiqueta (16) | 00009966
```

Los 14 bytes de cabecera tras el prefijo son los datos asociados (AAD) del GCM.

## Cifrado por versión

| Versión | Cifrado | Cabecera de versión (`3.x` + 12 ceros) |
|---|---|---|
| 3.1 | Solo el CONTROL: AES-ECB + base64, firmado con MD5 | En el CONTROL |
| 3.3 | AES-ECB con la clave local | Fuera del cifrado, salvo en consultas |
| 3.4 | AES-ECB con la clave de sesión + HMAC | Dentro del cifrado, salvo en consultas |
| 3.5 | AES-GCM con la clave de sesión | Dentro del cifrado, salvo en consultas |

## Negociación de sesión (3.4 y 3.5)

1. Cliente → `SESS_KEY_NEG_START` (3) con 16 bytes aleatorios `L`.
2. Dispositivo → `SESS_KEY_NEG_RESP` (4) con 16 bytes `R` + `HMAC(clave, L)`. Si el HMAC no coincide, la clave local es incorrecta.
3. Cliente → `SESS_KEY_NEG_FINISH` (5) con `HMAC(clave, R)`.
4. Clave de sesión = `L XOR R` cifrado con la clave local: AES-ECB en 3.4; AES-GCM (IV = `L[0:12]`, primeros 16 bytes) en 3.5.

## Comandos usados

| Código | Nombre | Uso |
|---|---|---|
| 7 / 13 | CONTROL / CONTROL_NEW | Cambiar DPs (3.1–3.3 / 3.4+) |
| 8 | STATUS | Aviso espontáneo de cambio |
| 9 | HEART_BEAT | Latido cada 9 s |
| 10 / 16 | DP_QUERY / DP_QUERY_NEW | Leer el estado |
| 18 | UPDATEDPS | Pedir nueva medición de consumo |

En 3.4+ el CONTROL va como `{"protocol":5,"t":…,"data":{"dps":{…}}}`. Para un subdispositivo Zigbee se añade `"cid"` (y `"ctype":0`), y la orden viaja por la conexión del hub.

Algunos equipos 3.3 responden `data unvalid` a la consulta normal («device22»); entonces se consulta con un CONTROL_NEW que lleva `{"dps":{"1":null}}`.

## DPs que interpreta la app

| Código Tuya | DP habitual | Significado |
|---|---|---|
| `switch_1` … `switch_6`, `switch` | 1–6 | Interruptor (booleano) |
| `switch_usb1` … | — | Puerto USB |
| `countdown_1` … | 9 en contactos, 7–12 en interruptores de pared | Temporizador en segundos |
| `cur_current` | 18 | Corriente en mA |
| `cur_power` | 19 | Potencia en décimas de W |
| `cur_voltage` | 20 | Voltaje en décimas de V |

Sin mapeo (archivo sin `mapping` o dispositivo agregado a mano), la app deduce el significado por el número y el tipo del valor.

## Comprobación

`test/gen_vectors.py` genera tramas con Python y la biblioteca `cryptography`, escritas aparte del código Java, y `test/Tests.java` exige que Java produzca exactamente los mismos bytes. `test/FakeDevice.java` simula el lado del dispositivo para probar sesiones completas.
