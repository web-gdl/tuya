"""Genera las imágenes del README (docs/screenshots/).

Son ilustraciones dibujadas con las mismas medidas, colores e iconos que el código
de la app (1 dp = 1 px, a doble densidad); NO son capturas de un teléfono real.

Requiere: playwright (chromium) y Pillow. La letra Roboto se toma de
@fontsource/roboto (npm pack @fontsource/roboto@5.1.0) si se indica su carpeta
en ROBOTO_DIR; si no, se usa la letra sans-serif del sistema.

    python3 docs/preview/render.py
"""
import base64
import os
from pathlib import Path

from PIL import Image
from playwright.sync_api import sync_playwright

OUT = Path(__file__).resolve().parents[1] / "screenshots"
ROBOTO = Path(os.environ.get("ROBOTO_DIR", "/tmp/roboto/package/files"))

LIGHT = dict(accent="#FF5A1F", on="#FFFFFF", bg="#F4F5F9", card="#FFFFFF", text="#1B1C1F",
             text2="#8A8D96", chip="#EEF0F4", line="#E3E5EB")
DARK = dict(LIGHT, bg="#0F1013", card="#1B1C21", text="#F1F2F4", text2="#9A9DA6", chip="#2A2C33", line="#2C2E35")

BOX = "M6,3 h12 a3,3 0 0 1 3,3 v12 a3,3 0 0 1 -3,3 h-12 a3,3 0 0 1 -3,-3 v-12 a3,3 0 0 1 3,-3 z"
# Los mismos trazos que res/drawable/ic_*.xml: (ruta, grosor)
ICONS = {
    "power": [("M12,3.5 v8", 2), ("M7.4,6.6 a6.6,6.6 0 1 0 9.2,0", 2)],
    "plug": [(BOX, 1.8), ("M9,8.5 v3.5", 1.8), ("M15,8.5 v3.5", 1.8), ("M12,15.5 v1", 1.8)],
    "switch": [(BOX, 1.8), ("M9.5,7.5 h5 v9 h-5 z", 1.8), ("M11,10.5 h2", 1.8)],
    "hub": [("M5,14 h14 a1.5,1.5 0 0 1 1.5,1.5 v3 a1.5,1.5 0 0 1 -1.5,1.5 h-14 a1.5,1.5 0 0 1 -1.5,-1.5 v-3 a1.5,1.5 0 0 1 1.5,-1.5 z", 1.8),
            ("M12,14 v-3.5", 1.8), ("M9,8 a4.2,4.2 0 0 1 6,0", 1.8), ("M6.5,5.5 a7.8,7.8 0 0 1 11,0", 1.8)],
    "add": [("M12,5 v14", 2), ("M5,12 h14", 2)],
    "home": [("M4,11 L12,4 L20,11 V20 H14.5 V14.5 H9.5 V20 H4 Z", 1.8)],
    "settings": [("M4,7 h8", 1.8), ("M18,7 h2", 1.8), ("M13,7 a2.5,2.5 0 1 0 5,0 a2.5,2.5 0 1 0 -5,0", 1.8),
                 ("M4,17 h2", 1.8), ("M12,17 h8", 1.8), ("M6,17 a2.5,2.5 0 1 0 5,0 a2.5,2.5 0 1 0 -5,0", 1.8)],
    "back": [("M15,5 L8,12 L15,19", 2)],
    "timer": [("M5,13.5 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0", 1.8), ("M12,9.5 v4 l2.5,2", 1.8), ("M10,3 h4", 1.8)],
    "next": [("M9,5 L16,12 L9,19", 1.8)],
}


def icon(name, size, color):
    if name == "more":
        dots = "".join('<circle cx="12" cy="%d" r="1.7" fill="%s"/>' % (y, color) for y in (5, 12, 19))
        return '<svg width="%d" height="%d" viewBox="0 0 24 24">%s</svg>' % (size, size, dots)
    paths = "".join('<path d="%s" fill="none" stroke="%s" stroke-width="%s" stroke-linecap="round" stroke-linejoin="round"/>'
                    % (d, color, w) for d, w in ICONS[name])
    return '<svg width="%d" height="%d" viewBox="0 0 24 24" style="flex:none">%s</svg>' % (size, size, paths)


def fonts():
    css = ""
    for weight in (400, 500):
        f = ROBOTO / ("roboto-latin-%d-normal.woff2" % weight)
        if f.exists():
            css += ("@font-face{font-family:Roboto;font-weight:%d;src:url(data:font/woff2;base64,%s)}"
                    % (weight, base64.b64encode(f.read_bytes()).decode()))
    return css


def css(c):
    return fonts() + """
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:Roboto,'DejaVu Sans',sans-serif;color:%(text)s;-webkit-font-smoothing:antialiased}
.phone{width:360px;height:800px;background:%(bg)s;display:flex;flex-direction:column;overflow:hidden;position:relative}
.sb{height:30px;display:flex;align-items:center;justify-content:space-between;padding:0 22px;font-size:13px;font-weight:500;flex:none}
.sb i{display:inline-block;width:14px;height:9px;border-radius:2px;background:%(text)s;opacity:.75;margin-left:5px}
.gest{height:22px;display:flex;justify-content:center;align-items:center;flex:none;background:var(--gb,%(bg)s)}
.gest b{width:108px;height:4px;border-radius:2px;background:%(text)s;opacity:.55}
.b{font-weight:500}.t2{color:%(text2)s}
.hdr{display:flex;align-items:center;padding:14px 16px 6px 20px}
.hdr h1{font-size:24px;font-weight:500;flex:1}
.rb{display:flex;align-items:center;justify-content:center;border-radius:50%%;flex:none}
.tabs{display:flex;align-items:center;padding:0 12px;white-space:nowrap}
.tabs span{padding:10px;font-size:15px;color:%(text2)s}
.tabs .sel{font-size:17px;font-weight:500;color:%(text)s}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:12px;padding:8px 16px 16px;align-content:start;flex:1}
.cardd{height:152px;background:%(card)s;border-radius:18px;padding:14px 14px 12px;display:flex;flex-direction:column}
.cardd .top{display:flex;justify-content:space-between;align-items:center}
.cardd .nm{font-size:15px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:auto}
.cardd .st{font-size:12px;color:%(text2)s;padding-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.chips{display:flex;gap:6px;height:30px;margin-top:8px}
.chips span{flex:1;border-radius:15px;display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:500}
.nav{height:58px;background:%(card)s;display:flex;flex:none}
.nav div{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;font-size:11px;gap:2px}
.bar{display:flex;align-items:center;padding:8px 8px 4px}
.bar .ib{width:44px;height:44px;display:flex;align-items:center;justify-content:center}
.bar .tt{flex:1;padding-left:4px}.bar .tt div:first-child{font-size:19px;font-weight:500}.bar .tt div+div{font-size:13px;color:%(text2)s}
.body{padding:8px 16px 24px;flex:1}
.gc{background:%(card)s;border-radius:16px;overflow:hidden}
.row{display:flex;align-items:center;padding:14px 12px 14px 16px;min-height:56px}
.row .c{flex:1}.row .c div:first-child{font-size:16px}.row .c div+div{font-size:13px;color:%(text2)s;padding-top:2px}
.dv{height:1px;background:%(line)s;margin-left:16px}
.sec{font-size:13px;font-weight:500;color:%(text2)s;padding:14px 0 8px 4px}
.pill{height:44px;border-radius:22px;background:%(card)s;display:flex;align-items:center;justify-content:center;font-size:14px}
.hero{display:flex;flex-direction:column;align-items:center;padding:36px 0 28px}
.met{display:flex;padding:14px 0}.met div{flex:1;text-align:center}.met b{display:block;font-size:20px;font-weight:500}.met span{font-size:12px;color:%(text2)s}
.srow{display:flex;align-items:center;padding:10px 12px 10px 16px;min-height:68px}
.srow .c{flex:1}.srow .c div:first-child{font-size:16px}.srow .c div+div{font-size:12px;color:%(accent)s}
""" % c


def power(c, on, size, online=True):
    return ('<div class="rb" style="width:%dpx;height:%dpx;background:%s;opacity:%s">%s</div>'
            % (size, size, c["accent"] if on else c["chip"], 1 if online else .45,
               icon("power", round(size * .52), c["on"] if on else c["text2"])))


def card(c, ic, name, status, gangs=None, on=False, online=True, has_power=True):
    lit = online and (on or (gangs and any(gangs)))
    chips = ""
    if gangs:
        chips = '<div class="chips">' + "".join(
            '<span style="background:%s;color:%s;opacity:%s">%d</span>'
            % (c["accent"] if g else c["chip"], c["on"] if g else c["text2"], 1 if online else .45, i + 1)
            for i, g in enumerate(gangs)) + "</div>"
    pw = power(c, bool(lit), 42, online) if has_power else '<div style="height:42px"></div>'
    return ('<div class="cardd"><div class="top">%s%s</div><div class="nm" style="opacity:%s">%s</div>'
            '<div class="st">%s</div>%s</div>'
            % (icon(ic, 34, c["accent"] if lit else c["text2"]), pw, 1 if online else .6, name, status, chips))


def frame(c, inner, nav_bg=None):
    return ('<div class="phone"><div class="sb"><span>9:41</span><span><i></i><i></i><i style="width:20px"></i></span></div>'
            '%s<div class="gest" style="--gb:%s"><b></b></div></div>' % (inner, nav_bg or c["bg"]))


def nav(c, settings=False):
    a, b = (c["text2"], c["accent"]) if settings else (c["accent"], c["text2"])
    return ('<div class="nav"><div style="color:%s">%sInicio</div><div style="color:%s">%sAjustes</div></div>'
            % (a, icon("home", 24, a), b, icon("settings", 24, b)))


def home(c):
    cards = "".join([
        card(c, "switch", "Luz de la sala", "Sala · 2 de 3 encendidos", gangs=[True, True, False]),
        card(c, "plug", "Cafetera", "Cocina · Encendido", on=True),
        card(c, "plug", "Lámpara de pie", "Sala · Apagado"),
        card(c, "switch", "Recámara", "Recámara · Apagado", gangs=[False, False]),
        card(c, "hub", "Hub Zigbee", "3 dispositivos", has_power=False),
        card(c, "plug", "Bomba del patio", "Patio · Sin conexión", online=False),
    ])
    tabs = '<div class="tabs"><span class="sel">Todos</span><span>Sala</span><span>Cocina</span><span>Recámara</span><span>Patio</span></div>'
    hdr = ('<div class="hdr"><h1>Mi hogar</h1><div class="rb" style="width:40px;height:40px;background:%s">%s</div></div>'
           % (c["accent"], icon("add", 21, c["on"])))
    return frame(c, hdr + tabs + '<div class="grid">' + cards + "</div>" + nav(c), c["card"])


def bar(c, title, sub=None, more=True):
    s = "<div>%s</div>" % sub if sub else ""
    m = '<div class="ib">%s</div>' % icon("more", 24, c["text"]) if more else ""
    return ('<div class="bar"><div class="ib">%s</div><div class="tt"><div>%s</div>%s</div>%s</div>'
            % (icon("back", 24, c["text"]), title, s, m))


def row(c, title, detail, arrow=True):
    d = "<div>%s</div>" % detail if detail else ""
    return ('<div class="row"><div class="c"><div>%s</div>%s</div>%s</div>'
            % (title, d, icon("next", 20, c["text2"]) if arrow else ""))


def rows(c, items):
    return '<div class="gc">' + ('<div class="dv"></div>'.join(row(c, *i) for i in items)) + "</div>"


def info(c, room, link, dev_id, sched=None):
    items = [("Programación", sched)] if sched else []
    return rows(c, items + [("Cuarto", room), ("Conexión", link), ("Identificador", dev_id, False)])


def detail_plug(c):
    hero = ('<div class="hero">%s<div class="b" style="font-size:20px;padding-top:18px">Encendido</div>'
            '<div class="pill" style="padding:0 20px;margin-top:18px">Se apaga en 30 min</div></div>' % power(c, True, 168))
    met = ('<div class="gc"><div class="met"><div><b>23.5 W</b><span>Potencia</span></div><div><b>127.1 V</b><span>Voltaje</span></div>'
           '<div><b>185 mA</b><span>Corriente</span></div></div></div>')
    body = '<div class="body">%s%s<div style="height:16px"></div>%s</div>' % (
        hero, met, info(c, "Cocina", "192.168.1.42 · protocolo 3.4", "eb1a2b3c4d5e6f7g8h9ijk",
                 "1 activo · se apaga hoy 9:00 p. m."))
    return frame(c, bar(c, "Cafetera", "En línea · red local") + body)


def detail_switch(c):
    def srow(name, on, timer=""):
        t = "<div>%s</div>" % timer if timer else ""
        return ('<div class="srow"><div class="c"><div>%s</div>%s</div><div class="rb" style="width:40px;height:40px;background:%s;margin-right:10px">%s</div>%s</div>'
                % (name, t, c["chip"], icon("timer", 21, c["text"]), power(c, on, 46)))
    allb = ('<div style="display:flex;gap:12px;padding:8px 0 16px"><div class="pill b" style="flex:1">Encender todo</div>'
            '<div class="pill b" style="flex:1">Apagar todo</div></div>')
    sw = '<div class="gc">%s<div class="dv"></div>%s<div class="dv"></div>%s</div>' % (
        srow("Techo", True), srow("Lámpara", True, "Se apaga en 1 h"), srow("Ventilador", False))
    body = '<div class="body">%s%s<div style="height:16px"></div>%s</div>' % (
        allb, sw, info(c, "Sala", "192.168.1.37 · protocolo 3.3", "bf9z8y7x6w5v4u3t2s1rqp",
                 "2 activos · se enciende hoy 7:00 p. m."))
    return frame(c, bar(c, "Luz de la sala", "En línea · red local") + body)


def add(c):
    keys = rows(c, [("Importar archivo de claves", "devices.json de tinytuya o un respaldo de esta app"),
                    ("Descargar de Tuya IoT", "Una sola vez, con tu cuenta de desarrollador"),
                    ("Agregar a mano", "Con identificador y clave local")])
    found = rows(c, [("Cafetera", "192.168.1.42 · protocolo 3.4 · ya agregado", False),
                     ("Dispositivo …k3m9qa", "192.168.1.51 · protocolo 3.3 · falta su clave local"),
                     ("Dispositivo …7t2xwd", "192.168.1.58 · protocolo 3.5 · falta su clave local")])
    hint = ('<div class="t2" style="font-size:13px;padding:0 4px 10px">Los dispositivos Tuya se anuncian solos cada pocos segundos. '
            'Aquí aparecen los que están en tu misma red Wi-Fi.</div>')
    body = '<div class="body"><div class="sec">Claves locales</div>%s<div class="sec">En tu red</div>%s%s</div>' % (keys, hint, found)
    return frame(c, bar(c, "Agregar dispositivos", more=False) + body)


def schedule(c):
    def toggle(on):
        return ('<div style="width:40px;height:22px;border-radius:11px;background:%s;position:relative;flex:none">'
                '<div style="position:absolute;top:2px;%s:2px;width:18px;height:18px;border-radius:50%%;background:#fff;'
                'box-shadow:0 1px 2px rgba(0,0,0,.3)"></div></div>' % (c["accent"] if on else c["chip"], "right" if on else "left"))

    def srow(t, what, on_action, extra, enabled=True):
        return ('<div class="row" style="padding:12px 12px 12px 16px;min-height:76px"><div class="c">'
                '<div style="font-size:26px;font-weight:500;opacity:%s">%s</div>'
                '<div style="font-size:14px;color:%s;opacity:%s">%s</div>'
                '<div style="font-size:12px;color:%s;padding-top:3px;line-height:1.35">%s</div></div>%s</div>'
                % (1 if enabled else .45, t, c["accent"] if on_action else c["text2"], 1 if enabled else .6, what,
                   c["text2"], extra, toggle(enabled)))

    def group(name, items):
        return ('<div class="sec">%s</div><div class="gc">%s</div>'
                % (name, '<div class="dv"></div>'.join(srow(*i) for i in items)))

    hint = ('<div class="t2" style="font-size:13px;padding:12px 14px;border-radius:14px">Los horarios los cumple este teléfono: '
            'a la hora indicada se conecta al dispositivo por tu Wi-Fi. Debe estar encendido y en la red de casa.</div>')
    body = ('<div class="body">%s%s%s%s</div>' % (
        hint,
        group("Luz de la sala", [
            ("7:00 p. m.", "Encender · Todos los días · Techo", True, "Próxima: hoy 7:00 p. m.<br>Última: ayer 7:00 p. m. · cumplido"),
            ("11:30 p. m.", "Apagar · Todos los días", False, "Próxima: hoy 11:30 p. m.<br>Última: ayer 11:30 p. m. · cumplido")]),
        group("Cafetera", [
            ("6:30 a. m.", "Encender · Lunes a viernes", True, "Próxima: lunes 6:30 a. m.<br>Última: hoy 6:30 a. m. · cumplido"),
            ("8:00 a. m.", "Apagar · Lunes a viernes", False, "Próxima: lunes 8:00 a. m.")]),
        group("Bomba del patio", [
            ("6:00 a. m.", "Encender · Mar, Jue, Sáb", True, "Desactivado", False)])))
    hdr = ('<div class="bar"><div class="ib">%s</div><div class="tt"><div>Programación</div></div>'
           '<div class="rb" style="width:40px;height:40px;background:%s;margin-right:8px">%s</div></div>'
           % (icon("back", 24, c["text"]), c["accent"], icon("add", 21, c["on"])))
    return frame(c, hdr + body)


def editor(c):
    days = "".join('<span style="flex:1;height:40px;border-radius:21px;display:flex;align-items:center;justify-content:center;'
                   'font-weight:500;background:%s;color:%s">%s</span>'
                   % (c["accent"] if on else c["chip"], c["on"] if on else c["text2"], l)
                   for l, on in zip("LMMJVSD", [1, 1, 1, 1, 1, 0, 0]))
    pill = ('<span style="flex:1;height:42px;border-radius:21px;display:flex;align-items:center;justify-content:center;'
            'font-weight:500;background:%s;color:%s">%s</span>')
    lab = '<div class="t2" style="font-size:12px;padding:10px 0 4px 4px">%s</div>'
    chk = ('<div style="display:flex;align-items:center;gap:12px;padding:7px 2px;font-size:16px">'
           '<span style="width:18px;height:18px;border-radius:3px;background:%s;border:2px solid %s"></span>%s</div>')
    dlg = ('<div style="position:absolute;left:20px;right:20px;top:110px;background:%s;border-radius:24px;padding:22px 22px 14px;'
           'box-shadow:0 12px 40px rgba(0,0,0,.25)">'
           '<div style="font-size:20px;padding-bottom:14px">Nuevo horario · Luz de la sala</div>'
           '<div style="height:76px;border-radius:16px;background:%s;display:flex;align-items:center;justify-content:center;'
           'font-size:44px;font-weight:500">11:30 p. m.</div>'
           + lab % "Acción" + '<div style="display:flex;gap:10px">' + pill % (c["chip"], c["text2"], "Encender")
           + pill % (c["accent"], c["on"], "Apagar") + '</div>'
           + lab % "Días" + '<div style="display:flex;gap:5px">' + days + '</div>'
           '<div style="display:flex;gap:18px;padding:12px 4px 2px;font-size:13px;font-weight:500;color:%s">'
           '<span>Todos</span><span>Lun–Vie</span><span>Sáb–Dom</span></div>'
           + lab % "Teclas" + chk % (c["accent"], c["accent"], "Techo") + chk % (c["accent"], c["accent"], "Lámpara")
           + chk % ("transparent", c["text2"], "Ventilador") +
           '<div style="display:flex;justify-content:flex-end;gap:28px;padding:18px 6px 6px;color:%s;font-weight:500;font-size:14px">'
           '<span>CANCELAR</span><span>GUARDAR</span></div></div>') % (c["card"], c["chip"], c["accent"], c["accent"])
    shade = '<div style="position:absolute;inset:0;background:rgba(0,0,0,.42)"></div>'
    return schedule(c).replace('<div class="gest"', shade + dlg + '<div class="gest"', 1)


def page(c, inner, extra=""):
    return "<html><head><meta charset='utf-8'><style>%s%s</style></head><body>%s</body></html>" % (css(c), extra, inner)


def cover():
    c = LIGHT
    extra = """
body{width:1280px;height:640px;background:linear-gradient(120deg,#FF5A1F 0%,#FF8A3D 100%);overflow:hidden;position:relative}
.txt{position:absolute;left:72px;top:150px;color:#fff;width:520px}
.txt h1{font-size:68px;font-weight:500;letter-spacing:-1px}
.txt p{font-size:26px;line-height:1.35;padding-top:18px;opacity:.96}
.txt .tags{padding-top:30px;font-size:18px;display:flex;gap:10px;flex-wrap:wrap}
.txt .tags span{background:rgba(255,255,255,.2);border-radius:20px;padding:8px 16px}
.ph{position:absolute;border-radius:34px;overflow:hidden;box-shadow:0 24px 60px rgba(0,0,0,.28);border:6px solid #16171a;transform-origin:top left}
"""
    dark_css = css(DARK).split("*{box-sizing", 1)[1]
    scoped = ""
    # La maqueta oscura lleva sus colores en línea: se reutiliza el mismo CSS con un prefijo.
    for rule in ("*{box-sizing" + dark_css).split("}"):
        if "{" not in rule:
            continue
        sel, decl = rule.split("{", 1)
        scoped += ",".join(".dk " + s.strip() for s in sel.split(",") if s.strip()) + "{" + decl + "}"
    inner = ('<div class="txt"><h1>Hogar Local</h1><p>Tus interruptores y contactos Tuya, controlados por tu propia red.</p>'
             '<div class="tags"><span>Sin cuenta</span><span>Sin nube</span><span>Sin rastreo</span><span>Horarios</span><span>90 KB</span></div></div>'
             '<div class="ph" style="left:650px;top:70px;transform:scale(.78)">%s</div>'
             '<div class="ph dk" style="left:960px;top:150px;transform:scale(.78);color:%s">%s</div>'
             % (home(LIGHT), DARK["text"], detail_plug(DARK)))
    return page(c, inner, extra + scoped)


def shoot(pg, html, path, w, h, scale):
    pg.set_viewport_size({"width": w, "height": h})
    pg.set_content(html)
    pg.wait_for_timeout(250)
    tmp = str(path) + ".tmp.png"
    pg.screenshot(path=tmp)
    # 256 colores: imágenes ligeras para el repositorio.
    Image.open(tmp).convert("RGB").quantize(256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).save(path, optimize=True)
    os.remove(tmp)
    print(path.name, os.path.getsize(path) // 1024, "KB")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as p:
        b = p.chromium.launch()
        phone = b.new_context(device_scale_factor=2).new_page()
        shots = [("01-inicio", page(LIGHT, home(LIGHT))), ("02-contacto", page(LIGHT, detail_plug(LIGHT))),
                 ("03-interruptor", page(LIGHT, detail_switch(LIGHT))), ("04-agregar", page(LIGHT, add(LIGHT))),
                 ("05-oscuro", page(DARK, home(DARK))), ("06-programacion", page(LIGHT, schedule(LIGHT))),
                 ("07-horario", page(LIGHT, editor(LIGHT)))]
        for name, html in shots:
            shoot(phone, html, OUT / (name + ".png"), 360, 800, 2)
        wide = b.new_context(device_scale_factor=1).new_page()
        shoot(wide, cover(), OUT / "00-portada.png", 1280, 640, 1)
        b.close()


if __name__ == "__main__":
    main()
