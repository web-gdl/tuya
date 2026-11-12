package app.hogarlocal.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import app.hogarlocal.proto.Json;

/** Decide qué significa cada DP: interruptores, temporizadores y mediciones. */
public final class DpLogic {
    private DpLogic() { }

    public static boolean isSwitch(Dp d) {
        if (!"bool".equals(d.type)) return false;
        if (d.code.isEmpty()) {
            int n = d.num();
            return n >= 1 && n <= 6;
        }
        return d.code.matches("switch(_\\d+|_usb\\d*)?");
    }

    public static List<Dp> switches(Device dev) {
        List<Dp> out = new ArrayList<>();
        for (Dp d : dev.dps) if (isSwitch(d)) out.add(d);
        Collections.sort(out, (a, b) -> Integer.compare(a.num(), b.num()));
        return out;
    }

    /** DP de cuenta atrás (segundos) que corresponde a un interruptor, o null. */
    public static Dp countdownFor(Device dev, Dp sw) {
        Dp c;
        if (!sw.code.isEmpty()) {
            c = dev.dpByCode(sw.code.replaceFirst("^switch", "countdown"));
        } else {
            int n = sw.num();
            Dp seven = dev.dp("7");
            boolean wall = seven != null && "int".equals(seven.type) && seven.code.isEmpty();
            c = dev.dp(String.valueOf(wall ? n + 6 : n + 8));
            if (c != null && !c.code.isEmpty()) c = null;
        }
        return c != null && "int".equals(c.type) ? c : null;
    }

    public static String label(Dp sw, int index, int total) {
        if (!sw.name.isEmpty()) return sw.name;
        if (sw.code.startsWith("switch_usb")) {
            String n = sw.code.substring("switch_usb".length());
            return n.isEmpty() ? "USB" : "USB " + n;
        }
        return total == 1 ? "Encendido" : "Interruptor " + (index + 1);
    }

    private static String typeOf(Object v) {
        if (v instanceof Boolean) return "bool";
        if (v instanceof Number) return "int";
        return "str";
    }

    /** Aprende DPs nuevos a partir de un estado recibido. Devuelve true si hubo cambios. */
    public static boolean learn(Device dev, Map<String, Object> dps) {
        boolean changed = false;
        for (Map.Entry<String, Object> e : dps.entrySet()) {
            if (e.getValue() == null) continue;
            Dp d = dev.dp(e.getKey());
            String t = typeOf(e.getValue());
            if (d == null) {
                d = new Dp();
                d.id = e.getKey();
                d.type = t;
                dev.dps.add(d);
                changed = true;
            } else if (!t.equals(d.type)) {
                d.type = t;
                changed = true;
            }
        }
        return changed;
    }

    private static String mapType(String tuyaType) {
        if (tuyaType == null) return "";
        switch (tuyaType.toLowerCase()) {
            case "boolean": case "bool": return "bool";
            case "integer": case "value": return "int";
            default: return "str";
        }
    }

    private static boolean put(Device dev, String id, String code, String type) {
        if (id == null || id.isEmpty() || code == null) return false;
        Dp d = dev.dp(id);
        boolean changed = false;
        if (d == null) {
            d = new Dp();
            d.id = id;
            dev.dps.add(d);
            changed = true;
        }
        if (!code.isEmpty() && !code.equals(d.code)) { d.code = code; changed = true; }
        if (d.type.isEmpty() && !type.isEmpty()) { d.type = type; changed = true; }
        return changed;
    }

    /** Mapeo en el formato de tinytuya: {"1": {"code": "switch_1", "type": "Boolean"}, …}. */
    @SuppressWarnings("unchecked")
    public static boolean applyMapping(Device dev, Map<String, Object> mapping) {
        boolean changed = false;
        if (mapping == null) return false;
        for (Map.Entry<String, Object> e : mapping.entrySet()) {
            if (!(e.getValue() instanceof Map)) continue;
            Map<String, Object> x = (Map<String, Object>) e.getValue();
            changed |= put(dev, e.getKey(), Json.str(x, "code", ""), mapType(Json.str(x, "type", null)));
        }
        return changed;
    }

    /** Respuesta de /v1.0/devices/{id}/specifications de la nube de Tuya. */
    @SuppressWarnings("unchecked")
    public static boolean applySpecs(Device dev, Map<String, Object> specs) {
        boolean changed = false;
        if (specs == null) return false;
        for (String section : new String[]{"status", "functions"}) {
            List<Object> l = Json.arr(specs, section);
            if (l == null) continue;
            for (Object o : l) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> x = (Map<String, Object>) o;
                changed |= put(dev, Json.str(x, "dp_id", null), Json.str(x, "code", ""), mapType(Json.str(x, "type", null)));
            }
        }
        return changed;
    }

    private static Dp metric(Device dev, String code, String fallbackId) {
        Dp d = dev.dpByCode(code);
        if (d == null) {
            // Sin mapeo: disposición habitual de los contactos con medidor (18, 19 y 20).
            Dp a = dev.dp("18"), b = dev.dp("19"), c = dev.dp("20");
            if (a != null && b != null && c != null && a.code.isEmpty() && b.code.isEmpty() && c.code.isEmpty()
                    && "int".equals(a.type) && "int".equals(b.type) && "int".equals(c.type)) {
                d = dev.dp(fallbackId);
            }
        }
        return d;
    }

    private static Double value(Device dev, Dp d, double divisor) {
        if (d == null) return null;
        Object v = dev.values.get(d.id);
        return v instanceof Number ? ((Number) v).doubleValue() / divisor : null;
    }

    public static Double watts(Device dev) { return value(dev, metric(dev, "cur_power", "19"), 10); }
    public static Double volts(Device dev) { return value(dev, metric(dev, "cur_voltage", "20"), 10); }
    public static Double milliamps(Device dev) { return value(dev, metric(dev, "cur_current", "18"), 1); }

    /** Ids de los DP de medición, para pedirle al dispositivo que los actualice. */
    public static List<Integer> metricIds(Device dev) {
        List<Integer> out = new ArrayList<>();
        Dp[] ms = {metric(dev, "cur_current", "18"), metric(dev, "cur_power", "19"), metric(dev, "cur_voltage", "20")};
        for (Dp d : ms) if (d != null && d.num() > 0) out.add(d.num());
        return out;
    }
}
