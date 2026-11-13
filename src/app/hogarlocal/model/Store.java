package app.hogarlocal.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.hogarlocal.proto.Json;
import app.hogarlocal.sched.Schedule;

/** Hogar, cuartos y dispositivos guardados en un único JSON privado de la app. */
public final class Store {
    public String homeName = "Mi hogar";
    public final List<String> rooms = new ArrayList<>();
    public final List<Device> devices = new ArrayList<>();
    public final List<Schedule> schedules = new ArrayList<>();

    private final File file;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "store-io");
        t.setDaemon(true);
        return t;
    });

    public Store(File file) { this.file = file; }

    public Device find(String id) {
        for (Device d : devices) if (d.id.equals(id)) return d;
        return null;
    }

    public Schedule schedule(String id) {
        for (Schedule s : schedules) if (s.id.equals(id)) return s;
        return null;
    }

    public List<Schedule> schedulesOf(String deviceId) {
        List<Schedule> out = new ArrayList<>();
        for (Schedule s : schedules) if (s.deviceId.equals(deviceId)) out.add(s);
        return out;
    }

    public List<Device> childrenOf(String id) {
        List<Device> out = new ArrayList<>();
        for (Device d : devices) if (d.isSub() && d.parent.equals(id)) out.add(d);
        return out;
    }

    public Map<String, Object> toJson(boolean withKeys) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("app", "hogarlocal");
        m.put("format", 1);
        m.put("home", homeName);
        m.put("rooms", new ArrayList<>(rooms));
        List<Object> l = new ArrayList<>();
        for (Device d : devices) l.add(d.toJson(withKeys));
        m.put("devices", l);
        List<Object> sc = new ArrayList<>();
        for (Schedule x : schedules) sc.add(x.toJson());
        m.put("schedules", sc);
        return m;
    }

    @SuppressWarnings("unchecked")
    public void fromJson(Map<String, Object> m) {
        homeName = Json.str(m, "home", "Mi hogar");
        rooms.clear();
        devices.clear();
        schedules.clear();
        List<Object> r = Json.arr(m, "rooms");
        if (r != null) for (Object o : r) if (o instanceof String && !rooms.contains(o)) rooms.add((String) o);
        List<Object> l = Json.arr(m, "devices");
        if (l != null) {
            for (Object o : l) {
                if (!(o instanceof Map)) continue;
                Device d = Device.fromJson((Map<String, Object>) o);
                if (!d.id.isEmpty() && find(d.id) == null) devices.add(d);
            }
        }
        List<Object> sc = Json.arr(m, "schedules");
        if (sc != null) {
            for (Object o : sc) {
                if (!(o instanceof Map)) continue;
                Schedule x = Schedule.fromJson((Map<String, Object>) o);
                if (x != null && find(x.deviceId) != null && schedule(x.id) == null) schedules.add(x);
            }
        }
    }

    public void load() {
        if (!file.exists()) return;
        try {
            byte[] b = new byte[(int) file.length()];
            try (FileInputStream in = new FileInputStream(file)) {
                int off = 0, n;
                while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            }
            fromJson(Json.parseObject(new String(b, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // Archivo dañado: se aparta para no perderlo y se empieza de cero.
            file.renameTo(new File(file.getPath() + ".bad"));
        }
    }

    /** Guarda en segundo plano con escritura atómica. Llamar desde el hilo que modifica los datos. */
    public void save() {
        final byte[] data = Json.write(toJson(true)).getBytes(StandardCharsets.UTF_8);
        io.execute(() -> {
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(data);
                out.getFD().sync();
            } catch (IOException e) {
                tmp.delete();
                return;
            }
            if (!tmp.renameTo(file)) tmp.delete();
        });
    }

    /** Solo para pruebas: espera a que termine lo pendiente. */
    public void flush() throws Exception {
        io.submit(() -> { }).get();
    }
}
