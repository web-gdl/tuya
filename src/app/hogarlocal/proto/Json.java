package app.hogarlocal.proto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON mínimo y sin dependencias (también corre en la JVM de las pruebas).
 * Objetos → LinkedHashMap, listas → ArrayList, números → Long o Double.
 * La salida es compacta: los dispositivos Tuya rechazan JSON con espacios.
 */
public final class Json {
    private final String s;
    private int p;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json j = new Json(text);
        j.ws();
        Object v = j.value(0);
        j.ws();
        if (j.p != j.s.length()) throw j.err("sobra texto");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object o = parse(text);
        if (!(o instanceof Map)) throw new IllegalArgumentException("JSON: se esperaba un objeto");
        return (Map<String, Object>) o;
    }

    private IllegalArgumentException err(String m) {
        return new IllegalArgumentException("JSON: " + m + " en " + p);
    }

    private void ws() {
        while (p < s.length()) {
            char c = s.charAt(p);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '﻿') p++; else break;
        }
    }

    private Object value(int depth) {
        if (depth > 64) throw err("demasiados niveles");
        if (p >= s.length()) throw err("fin inesperado");
        char c = s.charAt(p);
        switch (c) {
            case '{': return object(depth);
            case '[': return array(depth);
            case '"': return string();
            case 't': lit("true"); return Boolean.TRUE;
            case 'f': lit("false"); return Boolean.FALSE;
            case 'n': lit("null"); return null;
            default: return number();
        }
    }

    private void lit(String w) {
        if (!s.startsWith(w, p)) throw err("valor desconocido");
        p += w.length();
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> m = new LinkedHashMap<>();
        p++;
        ws();
        if (p < s.length() && s.charAt(p) == '}') { p++; return m; }
        while (true) {
            ws();
            if (p >= s.length() || s.charAt(p) != '"') throw err("se esperaba una clave");
            String k = string();
            ws();
            if (p >= s.length() || s.charAt(p) != ':') throw err("se esperaba ':'");
            p++;
            ws();
            m.put(k, value(depth + 1));
            ws();
            if (p >= s.length()) throw err("fin inesperado");
            char c = s.charAt(p++);
            if (c == '}') return m;
            if (c != ',') throw err("se esperaba ',' o '}'");
        }
    }

    private List<Object> array(int depth) {
        List<Object> l = new ArrayList<>();
        p++;
        ws();
        if (p < s.length() && s.charAt(p) == ']') { p++; return l; }
        while (true) {
            ws();
            l.add(value(depth + 1));
            ws();
            if (p >= s.length()) throw err("fin inesperado");
            char c = s.charAt(p++);
            if (c == ']') return l;
            if (c != ',') throw err("se esperaba ',' o ']'");
        }
    }

    private String string() {
        StringBuilder b = new StringBuilder();
        p++;
        while (true) {
            if (p >= s.length()) throw err("cadena sin cerrar");
            char c = s.charAt(p++);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            if (p >= s.length()) throw err("escape incompleto");
            char e = s.charAt(p++);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u':
                    if (p + 4 > s.length()) throw err("escape \\u incompleto");
                    try {
                        b.append((char) Integer.parseInt(s.substring(p, p + 4), 16));
                    } catch (NumberFormatException x) { throw err("escape \\u inválido"); }
                    p += 4;
                    break;
                default: b.append(e);
            }
        }
    }

    private Object number() {
        int st = p;
        boolean real = false;
        while (p < s.length()) {
            char c = s.charAt(p);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+') p++;
            else if (c == '.' || c == 'e' || c == 'E') { real = true; p++; }
            else break;
        }
        if (st == p) throw err("valor inesperado");
        String t = s.substring(st, p);
        try {
            if (!real) return Long.parseLong(t);
        } catch (NumberFormatException ignored) { }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException x) { throw err("número inválido"); }
    }

    // ---- escritura ----

    public static String write(Object o) {
        StringBuilder b = new StringBuilder();
        write(b, o, -1, 0);
        return b.toString();
    }

    /** Salida con sangría, solo para archivos que lee una persona (respaldo). */
    public static String pretty(Object o) {
        StringBuilder b = new StringBuilder();
        write(b, o, 2, 0);
        return b.toString();
    }

    private static void nl(StringBuilder b, int indent, int level) {
        if (indent < 0) return;
        b.append('\n');
        for (int i = 0; i < indent * level; i++) b.append(' ');
    }

    private static void write(StringBuilder b, Object o, int indent, int level) {
        if (o == null) { b.append("null"); return; }
        if (o instanceof String) { quote(b, (String) o); return; }
        if (o instanceof Boolean) { b.append(o.toString()); return; }
        if (o instanceof Double || o instanceof Float) {
            double d = ((Number) o).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) b.append("null");
            else if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(d);
            return;
        }
        if (o instanceof Number) { b.append(((Number) o).longValue()); return; }
        if (o instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) o;
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) b.append(',');
                first = false;
                nl(b, indent, level + 1);
                quote(b, String.valueOf(e.getKey()));
                b.append(':');
                if (indent >= 0) b.append(' ');
                write(b, e.getValue(), indent, level + 1);
            }
            if (!first) nl(b, indent, level);
            b.append('}');
            return;
        }
        if (o instanceof Iterable) {
            b.append('[');
            boolean first = true;
            for (Object v : (Iterable<?>) o) {
                if (!first) b.append(',');
                first = false;
                nl(b, indent, level + 1);
                write(b, v, indent, level + 1);
            }
            if (!first) nl(b, indent, level);
            b.append(']');
            return;
        }
        quote(b, o.toString());
    }

    private static void quote(StringBuilder b, String v) {
        b.append('"');
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }

    // ---- lectura cómoda ----

    public static String str(Map<String, Object> m, String k, String def) {
        Object v = m == null ? null : m.get(k);
        if (v == null) return def;
        if (v instanceof String) return (String) v;
        if (v instanceof Number || v instanceof Boolean) return write(v);
        return def;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v instanceof List ? (List<Object>) v : null;
    }

    public static boolean bool(Map<String, Object> m, String k, boolean def) {
        Object v = m == null ? null : m.get(k);
        return v instanceof Boolean ? (Boolean) v : def;
    }
}
