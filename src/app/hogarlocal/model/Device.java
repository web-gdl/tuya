package app.hogarlocal.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import app.hogarlocal.proto.Json;

public final class Device {
    public static final int CONNECTING = 0, ONLINE = 1, OFFLINE = 2;

    public String id = "", name = "", key = "", ip = "", version = "auto", room = "";
    /** Subdispositivo Zigbee: su «cid» (node_id) y el id del hub por el que se controla. */
    public String cid = "", parent = "";
    public String category = "", product = "";
    public final List<Dp> dps = new ArrayList<>();

    // Estado en vivo (no se guarda)
    public volatile int state = OFFLINE;
    public volatile String error;
    public final Map<String, Object> values = new ConcurrentHashMap<>();

    public boolean isSub() { return !cid.isEmpty(); }

    public Dp dp(String id) {
        for (Dp d : dps) if (d.id.equals(id)) return d;
        return null;
    }

    public Dp dpByCode(String code) {
        for (Dp d : dps) if (d.code.equals(code)) return d;
        return null;
    }

    public boolean isOn(Dp sw) { return Boolean.TRUE.equals(values.get(sw.id)); }

    public Map<String, Object> toJson(boolean withKey) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        if (withKey) m.put("key", key);
        if (!ip.isEmpty()) m.put("ip", ip);
        m.put("version", version);
        if (!room.isEmpty()) m.put("room", room);
        if (!cid.isEmpty()) m.put("node_id", cid);
        if (!parent.isEmpty()) m.put("parent", parent);
        if (!category.isEmpty()) m.put("category", category);
        if (!product.isEmpty()) m.put("product_name", product);
        List<Object> l = new ArrayList<>();
        for (Dp d : dps) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", d.id);
            if (!d.code.isEmpty()) x.put("code", d.code);
            if (!d.name.isEmpty()) x.put("name", d.name);
            if (!d.type.isEmpty()) x.put("type", d.type);
            l.add(x);
        }
        m.put("dps", l);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Device fromJson(Map<String, Object> m) {
        Device d = new Device();
        d.id = Json.str(m, "id", "").trim();
        d.name = Json.str(m, "name", "").trim();
        d.key = Json.str(m, "key", "");
        d.ip = Json.str(m, "ip", "");
        d.version = Json.str(m, "version", "auto");
        d.room = Json.str(m, "room", "");
        d.cid = Json.str(m, "node_id", "");
        d.parent = Json.str(m, "parent", "");
        d.category = Json.str(m, "category", "");
        d.product = Json.str(m, "product_name", "");
        List<Object> l = Json.arr(m, "dps");
        if (l != null) {
            for (Object o : l) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> x = (Map<String, Object>) o;
                Dp p = new Dp();
                p.id = Json.str(x, "id", "");
                p.code = Json.str(x, "code", "");
                p.name = Json.str(x, "name", "");
                p.type = Json.str(x, "type", "");
                if (!p.id.isEmpty() && d.dp(p.id) == null) d.dps.add(p);
            }
        }
        return d;
    }
}
