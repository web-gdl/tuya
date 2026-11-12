# Cómo obtener las claves locales

Hogar Local controla tus dispositivos sin nube, pero cada uno cifra su tráfico con una **clave local** que solo conoce la nube de Tuya. Hay que pedirla **una vez**.

## Opción A: Tuya IoT desde la app

Necesitas una cuenta de desarrollador gratuita. Los menús de la plataforma de Tuya cambian de vez en cuando; los nombres pueden variar un poco.

1. **Cuenta.** Regístrate en <https://platform.tuya.com> (también se llama iot.tuya.com).
2. **Proyecto.** *Cloud → Development → Create Cloud Project*.
   - *Industry*: Smart Home. *Development Method*: Smart Home.
   - *Data Center*: el mismo de tu cuenta de Smart Life. Para México y EE. UU. es **Western America**.
   - Acepta los servicios de API que propone (como mínimo *IoT Core* y *Authorization Token Management*).
3. **Enlazar tu cuenta.** Dentro del proyecto: *Devices → Link App Account → Add App Account*. Aparece un código QR. En Smart Life: *Yo* → icono de escanear (arriba a la derecha) → escanea y confirma. Tus dispositivos aparecerán en la lista.
4. **Credenciales.** En *Overview* copia el **Access ID / Client ID** y el **Access Secret / Client Secret**.
5. **En Hogar Local.** *+ → Descargar de Tuya IoT* → elige el mismo centro de datos → pega las dos credenciales → *Descargar*.

La app pide un token, baja la lista de dispositivos (con nombres, claves y funciones) y se desconecta. **No guarda las credenciales.**

### Si falla

| Mensaje | Qué revisar |
|---|---|
| `sign invalid` | Access ID o Secret mal copiados (espacios, letras cambiadas). |
| `permission deny` / `No permissions` | El proyecto no tiene la cuenta de Smart Life enlazada, o falta un servicio de API. |
| `token invalid` | Centro de datos distinto al del proyecto. |
| La lista llega vacía | La cuenta enlazada no es la que tiene los dispositivos, o el centro de datos no coincide. |
| «No se pudo conectar con Tuya» | El teléfono no tiene internet. |

La suscripción gratuita a *IoT Core* caduca cada cierto tiempo y Tuya pide renovarla desde la consola; solo importa si vuelves a descargar claves.

## Opción B: tinytuya en una computadora

```sh
pip install tinytuya
python -m tinytuya wizard
```

El asistente pide las mismas credenciales de la opción A y genera `devices.json`. Pásalo al teléfono e impórtalo con *+ → Importar archivo de claves*. Los nombres, las claves, los subdispositivos Zigbee y el mapeo de funciones se conservan.

## Opción C: a mano

*+ → Agregar a mano*, con el identificador (22 caracteres, empieza a menudo por `eb` o `bf`) y la clave local (16 caracteres). En *Agregar → En tu red* aparecen los dispositivos que se anuncian; al tocar uno, el identificador ya viene escrito.

## Cuándo hay que repetirlo

- Si **vuelves a emparejar** un dispositivo en Smart Life (por ejemplo, tras restablecerlo o cambiar de router), su clave cambia: verás «Clave local incorrecta» o «No responde». Vuelve a descargar o importar; la app actualiza la clave sin perder nombres ni cuartos.
- Un dispositivo nuevo también necesita su clave.

## Seguridad

Las claves se guardan en el almacenamiento privado de la app (la copia de seguridad automática de Android está desactivada). El respaldo exportado **incluye las claves**: guárdalo en un lugar seguro.
