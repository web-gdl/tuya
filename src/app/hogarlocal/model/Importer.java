package app.hogarlocal.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import app.hogarlocal.proto.Json;
import app.hogarlocal.proto.TuyaSession;

/**
 * Lee listas de dispositivos con sus claves: el devices.json de tinytuya,
 * la respuesta de la nube de Tuya o un respaldo de esta app.
 */
public final class Importer {
    private Importer() { }

    public static final class Result {
        public final List<Device> devices = new ArrayList<>();
        public final List<String> rooms = new ArrayList<>();
        public String homeName;
    }

    @SuppressWarnings("unchecked")
    public static Result parse(String text) {
        Object root = Json.parse(text);
        Result r = new Result();
        List<Object> list = null;
        if (root instanceof List) {
            list = (List<Object>) root;
        } else if (root instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) root;
            list = Json.arr(m, "devices");
            if ("hogarlocal".equals(Json.str(m, "app", ""))) {
                r.homeName = Json.str(m, "home", null);
                List<Object> rooms = Json.arr(m, "rooms");
                if (rooms != null) for (Object o : rooms) if (o instanceof String) r.rooms.add((String) o);
            }
            if (list == null) {
                Map<String, Object> res = Json.obj(m, "result");
                if (res != null) list = Json.arr(res, "devices");
                else list = Json.arr(m, "result");
            }
        }
        if (list == null) throw new IllegalArgumentException("El archivo no contiene una lista de dispositivos");
        for (Object o : list) {
            if (!(o instanceof Map)) continue;
            Device d = fromMap((Map<String, Object>) o);
            if (d != null) r.devices.add(d);
        }
        linkParents(r.devices, null);
        return r;
    }

    public static List<Device> fromCloud(List<Map<String, Object>> cloud) {
        List<Device> out = new ArrayList<>();
        for (Map<String, Object> m : cloud) {
            Device d = fromMap(m);
            if (d != null) out.add(d);
        }
        linkParents(out, null);
        return out;
    }

    /** Solo se aceptan direcciones de red local; la nube devuelve la IP pública, que no sirve. */
    public static boolean isLocalIp(String ip) {
        if (ip == null) return false;
        String[] p = ip.trim().split("\\.");
        if (p.length != 4) return false;
        int[] n = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                n[i] = Integer.parseInt(p[i]);
                if (n[i] < 0 || n[i] > 255) return false;
            }
        } catch (NumberFormatException e) { return false; }
        return n[0] == 10 || (n[0] == 172 && n[1] >= 16 && n[1] <= 31) || (n[0] == 192 && n[1] == 168)
                || (n[0] == 169 && n[1] == 254) || n[0] == 127;
    }

    @SuppressWarnings("unchecked")
    static Device fromMap(Map<String, Object> m) {
        Device d = Device.fromJson(m);
        if (d.id.isEmpty()) d.id = Json.str(m, "gwId", Json.str(m, "devId", "")).trim();
        if (d.id.isEmpty()) return null;
        if (d.key.isEmpty()) d.key = Json.str(m, "local_key", "");
        d.key = d.key.trim();
        if (d.name.isEmpty()) d.name = d.product.isEmpty() ? "Dispositivo" : d.product;
        if (!isLocalIp(d.ip)) d.ip = "";
        d.ip = d.ip.trim();
        if (!TuyaSession.isVersion(d.version)) {
            String v = Json.str(m, "ver", "");
            d.version = TuyaSession.isVersion(v) ? v : "auto";
        }
        if (Json.obj(m, "mapping") != null) DpLogic.applyMapping(d, Json.obj(m, "mapping"));
        boolean sub = Json.bool(m, "sub", false) || !d.cid.isEmpty();
        if (!sub) { d.cid = ""; d.parent = ""; }
        else if (d.cid.isEmpty()) d.cid = Json.str(m, "uuid", "");
        String gw = Json.str(m, "gateway_id", "");
        if (sub && d.parent.isEmpty() && !gw.isEmpty()) d.parent = gw;
        return d;
    }

    /** Un subdispositivo comparte la clave local de su hub: así se encuentra al padre. */
    public static void linkParents(List<Device> fresh, List<Device> existing) {
        for (Device d : fresh) {
            if (!d.isSub() || !d.parent.isEmpty() || d.key.isEmpty()) continue;
            Device found = null;
            int count = 0;
            for (int pass = 0; pass < 2; pass++) {
                List<Device> src = pass == 0 ? fresh : existing;
                if (src == null) continue;
                for (Device p : src) {
                    if (p != d && !p.isSub() && p.key.equals(d.key) && (found == null || !found.id.equals(p.id))) {
                        found = p;
                        count++;
                    }
                }
            }
            if (count == 1) d.parent = found.id;
        }
    }

    /** Fusiona en el hogar sin pisar nombres ni cuartos del usuario. @return {nuevos, actualizados} */
    public static int[] merge(Store store, Result r) {
        int added = 0, updated = 0;
        linkParents(r.devices, store.devices);
        for (String room : r.rooms) if (!store.rooms.contains(room)) store.rooms.add(room);
        for (Device n : r.devices) {
            Device o = store.find(n.id);
            if (o == null) {
                if (!n.room.isEmpty() && !store.rooms.contains(n.room)) store.rooms.add(n.room);
                store.devices.add(n);
                added++;
                continue;
            }
            boolean ch = false;
            if (!n.key.isEmpty() && !n.key.equals(o.key)) { o.key = n.key; ch = true; }
            if (!n.ip.isEmpty() && o.ip.isEmpty()) { o.ip = n.ip; ch = true; }
            if (!"auto".equals(n.version) && "auto".equals(o.version)) { o.version = n.version; ch = true; }
            if (!n.cid.isEmpty() && !n.cid.equals(o.cid)) { o.cid = n.cid; ch = true; }
            if (!n.parent.isEmpty() && !n.parent.equals(o.parent)) { o.parent = n.parent; ch = true; }
            if (o.category.isEmpty() && !n.category.isEmpty()) { o.category = n.category; ch = true; }
            if (o.product.isEmpty() && !n.product.isEmpty()) { o.product = n.product; ch = true; }
            for (Dp p : n.dps) {
                Dp q = o.dp(p.id);
                if (q == null) { o.dps.add(p); ch = true; }
                else {
                    if (!p.code.isEmpty() && !p.code.equals(q.code)) { q.code = p.code; ch = true; }
                    if (q.type.isEmpty() && !p.type.isEmpty()) { q.type = p.type; ch = true; }
                }
            }
            if (ch) updated++;
        }
        return new int[]{added, updated};
    }
}
