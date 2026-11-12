# Cómo obtener las claves locales

Hogar Local controla tus dispositivos sin nube, pero cada uno cifra su tráfico con una **clave local** de 16 caracteres que solo conoce la nube de Tuya. Hay que pedirla **una sola vez**.

**Contenido**

- [Antes de empezar](#antes-de-empezar)
- [Opción A: Tuya IoT desde la app (recomendada)](#opción-a-tuya-iot-desde-la-app-recomendada)
  - [1. Crear la cuenta de desarrollador](#1-crear-la-cuenta-de-desarrollador)
  - [2. Crear el proyecto Cloud](#2-crear-el-proyecto-cloud)
  - [3. Enlazar tu cuenta de Smart Life](#3-enlazar-tu-cuenta-de-smart-life)
  - [4. Comprobar que aparecen tus dispositivos](#4-comprobar-que-aparecen-tus-dispositivos)
  - [5. Copiar el Access ID y el Access Secret](#5-copiar-el-access-id-y-el-access-secret)
  - [6. Descargar las claves en Hogar Local](#6-descargar-las-claves-en-hogar-local)
  - [Si algo falla](#si-algo-falla)
- [Opción B: tinytuya en una computadora](#opción-b-tinytuya-en-una-computadora)
- [Opción C: a mano](#opción-c-a-mano)
- [Después: cortar el internet a los dispositivos (opcional)](#después-cortar-el-internet-a-los-dispositivos-opcional)
- [Cuándo hay que repetirlo](#cuándo-hay-que-repetirlo)
- [Seguridad](#seguridad)
- [¿Se puede emparejar sin la app de Tuya?](#se-puede-emparejar-sin-la-app-de-tuya)

---

## Antes de empezar

- Tus dispositivos deben estar **ya emparejados y funcionando** en la app **Smart Life** o **Tuya Smart**. Hogar Local no empareja dispositivos nuevos (ver [la última sección](#se-puede-emparejar-sin-la-app-de-tuya)).
- Los pasos 1 a 5 se hacen **en una computadora** con navegador. El 3 y el 6 también necesitan el teléfono.
- Todo es gratis.

> Tuya cambia su plataforma de vez en cuando. Si un botón tiene otro nombre o está en otro lugar, busca el más parecido; la idea es siempre la misma.

---

## Opción A: Tuya IoT desde la app (recomendada)

### 1. Crear la cuenta de desarrollador

1. Entra a **<https://platform.tuya.com>** (antes se llamaba iot.tuya.com).
2. **Sign Up** y regístrate con un correo. Puede ser el mismo de Smart Life o uno distinto; no importa.
3. Confirma el correo e inicia sesión.

### 2. Crear el proyecto Cloud

1. En el menú de la izquierda: **Cloud → Development**.
2. **Create Cloud Project** y llena el formulario:

   | Campo | Qué poner |
   |---|---|
   | Project Name | Lo que quieras, por ejemplo `Hogar Local` |
   | Description | Opcional |
   | Industry | **Smart Home** |
   | Development Method | **Smart Home** |
   | Data Center | **El mismo de tu cuenta de Smart Life** (ver tabla abajo) |

3. **Create**. En la siguiente pantalla te propone servicios de API: acéptalos con **Authorize**. Como mínimo deben estar *IoT Core* y *Authorization Token Management*.

**¿Qué centro de datos elegir?** El de la región con la que te registraste en Smart Life:

| Tu país | Data Center en Tuya IoT | En Hogar Local |
|---|---|---|
| México, EE. UU., Canadá, Latinoamérica | **Western America** | América (oeste) — México, EE. UU. |
| Este de EE. UU. | Eastern America | América (este) |
| España, Alemania, mayor parte de Europa | Central Europe | Europa central |
| Reino Unido, Francia, otros | Western Europe | Europa occidental |
| India | India | India |
| China | China | China |
| Sureste asiático | Singapore | Singapur |

> Para comprobarlo: dentro del proyecto, la dirección del navegador incluye `region=…`. **`region=AZ` es Western America.**

### 3. Enlazar tu cuenta de Smart Life

1. Dentro del proyecto, abre la pestaña **Devices**.
2. Abre la subpestaña **Link App Account** y pulsa **Add App Account**.
3. Aparece un **código QR**.
4. En el teléfono, abre **Smart Life** (o Tuya Smart):
   - Pestaña **Yo** (o *Perfil*).
   - Icono de **escanear**, arriba a la derecha.
   - Escanea el QR de la computadora y pulsa **Confirmar inicio de sesión**.
5. En la computadora elige **Automatic Link** si te lo pregunta (enlaza todos tus dispositivos de una vez) y confirma.

### 4. Comprobar que aparecen tus dispositivos

En **Devices → Link App Account** debe aparecer tu cuenta. Al abrirla se muestra el panel **Manage Devices** con la lista de dispositivos: nombre, **Device ID**, producto y estado (*Online*).

- Si están todos, sigue al paso 5.
- Si la lista sale vacía: el centro de datos del proyecto no coincide con el de tu cuenta, o enlazaste otra cuenta. Ver [Si algo falla](#si-algo-falla).

> ⚠️ **Aquí es donde mucha gente se atora:** el panel *Manage Devices* **no muestra** el Access ID ni el Access Secret. Ciérralo con la **X** de arriba a la derecha y sigue al paso 5.

### 5. Copiar el Access ID y el Access Secret

1. Cierra el panel *Manage Devices* (**X** arriba a la derecha).
2. En las pestañas del proyecto (**Overview · Authorization · Service API · Devices · …**), entra a **Overview**.
3. Busca la sección **Authorization Key**:

   | Dato | Cómo se ve | Qué hacer |
   |---|---|---|
   | **Access ID / Client ID** | Visible completo (unos 20 caracteres) | Cópialo |
   | **Access Secret / Client Secret** | Oculto con asteriscos `********` | Pulsa el **ojo** para verlo o el icono de **copiar** |

4. Si no aparece en *Overview*, búscala en la pestaña **Authorization**: según la versión de la plataforma, Tuya la mueve de lugar.

Pásalos al teléfono por un medio privado: escríbelos, o usa una nota o un mensaje a ti mismo que borres después. **No los publiques ni los mandes en capturas de pantalla.**

### 6. Descargar las claves en Hogar Local

1. Conecta el teléfono al **mismo Wi-Fi** que tus dispositivos. Por ahora necesita internet: es la única vez.
2. **Cierra Smart Life.** Muchos dispositivos solo aceptan una conexión local a la vez.
3. En Hogar Local pulsa **+ → Descargar de Tuya IoT**.
4. Completa el formulario:
   - **Centro de datos**: el mismo del paso 2 (para México: *América (oeste) — México, EE. UU.*).
   - **Access ID / Client ID**: pégalo.
   - **Access Secret / Client Secret**: pégalo.
5. **Descargar**.

La app pide un permiso temporal, baja nombres, claves y funciones de todos tus dispositivos, y se desconecta. **Las credenciales no se guardan en el teléfono.**

En unos segundos, cada tarjeta pasa de *Buscando en la red…* a *Encendido* o *Apagado*: los dispositivos se anuncian solos en la red y la app detecta su IP.

Después puedes:
- Mantener pulsada una tarjeta → **Mover a cuarto** o **Cambiar nombre**.
- Abrir un interruptor de varias teclas y tocar el nombre de cada tecla para cambiarlo.

### Si algo falla

**Al descargar en Hogar Local:**

| Mensaje | Causa probable | Solución |
|---|---|---|
| `sign invalid` | Access ID o Secret mal copiados | Vuelve a copiarlos; revisa que no haya espacios ni letras cambiadas |
| `token invalid` | Centro de datos distinto al del proyecto | Elige en Hogar Local el mismo del paso 2 |
| `permission deny` / `No permissions` | Falta un servicio de API o la cuenta no está enlazada | Repite el paso 2.3 (*Authorize*) y el paso 3 |
| La lista llega vacía | Enlazaste otra cuenta o el centro de datos no coincide | Revisa el paso 4 |
| «No se pudo conectar con Tuya» | El teléfono no tiene internet | Conéctalo y repite |

**Ya descargadas, en las tarjetas:**

| Lo que ves | Qué hacer |
|---|---|
| *Buscando en la red…* | El teléfono está en otra red, o el router bloquea la comunicación entre equipos (aislamiento de clientes o red de invitados). Pon la IP a mano en ⋮ → Conexión. |
| *Clave local incorrecta* / *No responde* | El dispositivo se volvió a emparejar y su clave cambió: repite el paso 6. También puede ser que Smart Life siga abierta. |
| *Hub sin conexión* / *No responde por el hub* | Dispositivos Zigbee: revisa que el hub esté encendido y en línea. Esta parte es experimental. |

**Sobre la suscripción:** el servicio gratuito *IoT Core* caduca cada cierto tiempo y Tuya pide renovarlo desde la consola (**Cloud → Cloud Services**). Solo importa si vuelves a descargar claves; tus dispositivos siguen funcionando en Hogar Local aunque caduque.

---

## Opción B: tinytuya en una computadora

Si prefieres hacerlo desde la computadora:

```sh
pip install tinytuya
python -m tinytuya wizard
```

El asistente pide las mismas credenciales del paso 5 y el centro de datos, y genera `devices.json`. Pásalo al teléfono e impórtalo con **+ → Importar archivo de claves**. Se conservan nombres, claves, subdispositivos Zigbee y el mapeo de funciones.

## Opción C: a mano

**+ → Agregar a mano**, con:
- **Identificador**: el *Device ID* del paso 4 (20 o 22 caracteres; muchos empiezan por `eb` o `bf`).
- **Clave local**: 16 caracteres. Se ve en la plataforma de Tuya en **Cloud → API Explorer → Device Management → Query device details** con el Device ID (campo `local_key`).

En **+ → En tu red** aparecen los dispositivos que se anuncian; al tocar uno, el identificador ya viene escrito.

---

## Después: cortar el internet a los dispositivos (opcional)

Cuando todo funcione en Hogar Local:

1. Puedes **desinstalar Smart Life**.
2. En tu router, **bloquea el acceso a internet** de los dispositivos Tuya (control parental, lista de bloqueo por dirección MAC o una red aparte para ellos). Así solo hablan con tu teléfono dentro de tu casa.

**No los restablezcas ni los vuelvas a emparejar**: su clave local cambiaría y necesitarías internet y Smart Life otra vez.

## Cuándo hay que repetirlo

- Si **vuelves a emparejar** un dispositivo (por ejemplo, tras restablecerlo o cambiar de router), su clave cambia. Repite el paso 6: Hogar Local actualiza la clave **sin perder nombres ni cuartos**.
- Si agregas un dispositivo nuevo.

## Seguridad

- **El Access Secret es como una contraseña**: con él se pueden controlar tus dispositivos por la nube. No lo compartas ni lo subas a internet. Si se te escapa, en la consola de Tuya puedes borrar el proyecto y crear otro.
- Las claves locales se guardan en el almacenamiento privado de la app. La copia de seguridad automática de Android está desactivada.
- El **respaldo exportado incluye las claves**: guárdalo en un lugar seguro.

## ¿Se puede emparejar sin la app de Tuya?

Con el firmware de fábrica, no. El emparejamiento de Tuya siempre pasa por su nube: al conectarse al Wi-Fi, el dispositivo se activa con los servidores de Tuya y son ellos quienes le asignan la clave local. Sin esa activación no acepta órdenes, ni locales ni remotas.

Alternativas para quien quiera ir más lejos (fuera del alcance de esta app):

- **[tuya-cloudcutter](https://github.com/tuya-cloudcutter/tuya-cloudcutter)**, modo *detach*: suplanta a la nube durante el emparejamiento y deja el firmware original con una clave conocida, sin nube nunca. Necesita una computadora Linux con Wi-Fi y solo funciona con firmwares vulnerables, en su mayoría con chips Beken BK7231. Tuya corrigió la falla en su SDK a partir de 2022, así que muchos dispositivos recientes no sirven. Revisa su lista de firmwares parcheados. La clave que entrega se usa en Hogar Local con **Agregar a mano**.
- **Cambiar el firmware** por uno libre (OpenBeken, ESPHome, Tasmota), por OTA con cloudcutter o soldando. El dispositivo deja de usar el protocolo de Tuya y Hogar Local ya no podría controlarlo.
