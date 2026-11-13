package app.hogarlocal.sched;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.hogarlocal.proto.Json;

/** Una regla semanal: a tal hora, tales días, encender o apagar tales teclas de un dispositivo. */
public final class Schedule {
    /** Bit 0 = lunes … bit 6 = domingo. */
    public static final int WEEKDAYS = 0b0011111, WEEKEND = 0b1100000, EVERY_DAY = 0b1111111;

    public String id = "";
    public String deviceId = "";
    public int hour, minute;
    public int days = EVERY_DAY;
    public boolean on = true;
    public boolean enabled = true;
    /** Teclas (ids de DP) a cambiar; vacío = todas las del dispositivo. */
    public final List<String> dps = new ArrayList<>();

    // Última ejecución (se guarda para mostrarla)
    public long lastRun;
    /** "ok", "sin_confirmar" o un código de error; vacío si nunca se ejecutó. */
    public String lastResult = "";

    public static String newId() {
        return Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1296), 36);
    }

    public boolean hasDay(int index) { return (days & (1 << index)) != 0; }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("device", deviceId);
        m.put("time", String.format("%02d:%02d", hour, minute));
        m.put("days", days);
        m.put("action", on ? "on" : "off");
        m.put("enabled", enabled);
        if (!dps.isEmpty()) m.put("dps", new ArrayList<>(dps));
        if (lastRun > 0) {
            m.put("last_run", lastRun);
            m.put("last_result", lastResult);
        }
        return m;
    }

    /** null si el dato no es válido. */
    public static Schedule fromJson(Map<String, Object> m) {
        Schedule s = new Schedule();
        s.id = Json.str(m, "id", "");
        s.deviceId = Json.str(m, "device", "");
        String t = Json.str(m, "time", "");
        if (s.id.isEmpty() || s.deviceId.isEmpty() || !t.matches("\\d{1,2}:\\d{2}")) return null;
        String[] p = t.split(":");
        s.hour = Integer.parseInt(p[0]);
        s.minute = Integer.parseInt(p[1]);
        if (s.hour > 23 || s.minute > 59) return null;
        Object d = m.get("days");
        s.days = d instanceof Number ? ((Number) d).intValue() & EVERY_DAY : EVERY_DAY;
        if (s.days == 0) return null;
        s.on = !"off".equals(Json.str(m, "action", "on"));
        s.enabled = Json.bool(m, "enabled", true);
        List<Object> l = Json.arr(m, "dps");
        if (l != null) for (Object o : l) if (o != null && !s.dps.contains(String.valueOf(o))) s.dps.add(String.valueOf(o));
        Object lr = m.get("last_run");
        if (lr instanceof Number) {
            s.lastRun = ((Number) lr).longValue();
            s.lastResult = Json.str(m, "last_result", "");
        }
        return s;
    }

    private static final String[] SHORT = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};

    /** «Todos los días», «Lunes a viernes», «Lun, Mié, Vie»… */
    public static String daysText(int days) {
        days &= EVERY_DAY;
        if (days == EVERY_DAY) return "Todos los días";
        if (days == WEEKDAYS) return "Lunes a viernes";
        if (days == WEEKEND) return "Fines de semana";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((days & (1 << i)) == 0) continue;
            if (b.length() > 0) b.append(", ");
            b.append(SHORT[i]);
        }
        return b.toString();
    }
}
