package app.hogarlocal;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.hogarlocal.model.Device;
import app.hogarlocal.model.DpLogic;
import app.hogarlocal.model.Store;
import app.hogarlocal.proto.Discovery;
import app.hogarlocal.proto.TuyaSession;

/**
 * Estado vivo del hogar: mantiene las conexiones locales mientras la app está
 * en pantalla y las cierra al salir (no hay servicios ni trabajo en segundo plano).
 * Todos los datos se tocan solo desde el hilo principal.
 */
public final class Home implements TuyaSession.Listener, Discovery.Listener {
    public interface Observer {
        void onHomeChanged();
    }

    private static final int PORT = 6668;
    private static final long CLOSE_DELAY_MS = 4000;
    private static Home instance;

    public final Store store;
    /** Dispositivos que se anuncian en la red, por id. */
    public final Map<String, Discovery.Found> found = new LinkedHashMap<>();

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Observer> observers = new ArrayList<>();
    private final Map<String, TuyaSession> sessions = new HashMap<>();
    private final Map<String, String> signatures = new HashMap<>();
    private Discovery discovery;
    private WifiManager.MulticastLock lock;
    private int users;
    private boolean active, notifyPending;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!active) return;
            long now = System.currentTimeMillis();
            for (TuyaSession s : sessions.values()) s.tick(now);
            main.postDelayed(this, 1000);
        }
    };
    private final Runnable closer = this::shutdown;
    private final Runnable notifier = () -> {
        notifyPending = false;
        for (Observer o : new ArrayList<>(observers)) o.onHomeChanged();
    };

    private Home(Context ctx) {
        app = ctx.getApplicationContext();
        store = new Store(new File(app.getFilesDir(), "hogar.json"));
        store.load();
    }

    public static Home get(Context ctx) {
        if (instance == null) instance = new Home(ctx);
        return instance;
    }

    public void addObserver(Observer o) { if (!observers.contains(o)) observers.add(o); }
    public void removeObserver(Observer o) { observers.remove(o); }

    public void changed() {
        if (notifyPending) return;
        notifyPending = true;
        main.post(notifier);
    }

    /** Una pantalla visible pide conexiones. */
    public void acquire() {
        users++;
        main.removeCallbacks(closer);
        if (active) return;
        active = true;
        try {
            WifiManager wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
            lock = wm.createMulticastLock("hogarlocal");
            lock.setReferenceCounted(false);
            lock.acquire();
        } catch (RuntimeException e) {
            lock = null;
        }
        discovery = new Discovery(this);
        final Discovery d = discovery;
        new Thread(d::start, "udp-start").start();
        sync();
        main.postDelayed(ticker, 1000);
    }

    /** La pantalla dejó de verse; se espera un momento por si otra toma el relevo. */
    public void release() {
        users = Math.max(0, users - 1);
        if (users == 0) main.postDelayed(closer, CLOSE_DELAY_MS);
    }

    /** Si la app tiene ya una conexión viva con ese equipo (o su hub), para no abrir otra. */
    public boolean isLive(Device d) {
        TuyaSession s = active ? sessions.get(d.isSub() ? d.parent : d.id) : null;
        return s != null && s.state() == TuyaSession.ONLINE;
    }

    public boolean discoveryBlocked() {
        return discovery != null && active && discovery.boundPorts() == 0;
    }

    private void shutdown() {
        if (users > 0 || !active) return;
        active = false;
        main.removeCallbacks(ticker);
        for (TuyaSession s : sessions.values()) s.close();
        sessions.clear();
        signatures.clear();
        if (discovery != null) new Thread(discovery::stop, "udp-stop").start();
        discovery = null;
        if (lock != null) {
            try { lock.release(); } catch (RuntimeException ignored) { }
            lock = null;
        }
        for (Device d : store.devices) {
            d.state = Device.OFFLINE;
            d.error = null;
        }
    }

    public static boolean validKey(String key) { return key != null && key.length() == 16; }

    /** Ajusta las conexiones a lo que hay guardado. Llamar tras cualquier cambio de dispositivos. */
    public void sync() {
        if (!active) return;
        Set<String> wanted = new HashSet<>();
        for (Device d : store.devices) {
            if (d.isSub()) {
                Device p = store.find(d.parent);
                if (p == null) { d.state = Device.OFFLINE; d.error = "sin_hub"; }
                continue;
            }
            if (!validKey(d.key)) { d.state = Device.OFFLINE; d.error = "sin_clave"; continue; }
            if (d.ip.isEmpty()) { d.state = Device.OFFLINE; d.error = "sin_ip"; continue; }
            wanted.add(d.id);
            String sig = d.ip + "|" + d.key + "|" + d.version;
            TuyaSession s = sessions.get(d.id);
            if (s == null || !sig.equals(signatures.get(d.id))) {
                if (s != null) s.close();
                s = new TuyaSession(d.id, d.ip, PORT, d.key, d.version, this);
                sessions.put(d.id, s);
                signatures.put(d.id, sig);
                d.state = Device.CONNECTING;
                d.error = null;
                setChildren(s, d);
                s.start();
            } else {
                setChildren(s, d);
            }
        }
        for (Iterator<Map.Entry<String, TuyaSession>> it = sessions.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, TuyaSession> e = it.next();
            if (!wanted.contains(e.getKey())) {
                e.getValue().close();
                signatures.remove(e.getKey());
                it.remove();
            }
        }
        changed();
    }

    private void setChildren(TuyaSession s, Device hub) {
        Set<String> cids = new HashSet<>();
        for (Device c : store.childrenOf(hub.id)) cids.add(c.cid);
        s.setChildren(cids);
    }

    /** Reintenta ya mismo (por ejemplo al tirar para actualizar). */
    public void reconnectAll() {
        if (!active) return;
        for (TuyaSession s : sessions.values()) s.close();
        sessions.clear();
        signatures.clear();
        sync();
    }

    public void save() {
        store.save();
    }

    public void remove(Device d) {
        store.devices.remove(d);
        store.schedules.removeIf(x -> x.deviceId.equals(d.id));
        for (Device c : store.childrenOf(d.id)) c.parent = "";
        store.save();
        Scheduler.update(app);
        sync();
    }

    // ---- control ----

    public boolean control(Device d, Map<String, Object> dps) {
        TuyaSession s = sessions.get(d.isSub() ? d.parent : d.id);
        return s != null && s.control(d.isSub() ? d.cid : null, dps);
    }

    public boolean set(Device d, String dpId, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(dpId, value);
        return control(d, m);
    }

    public void refreshMetrics(Device d) {
        if (d.isSub()) return;
        TuyaSession s = sessions.get(d.id);
        List<Integer> ids = DpLogic.metricIds(d);
        if (s != null && !ids.isEmpty()) s.refresh(ids);
    }

    // ---- avisos de los hilos de red (se pasan al hilo principal) ----

    @Override public void onState(final TuyaSession s, final int state, final String error) {
        main.post(() -> {
            if (sessions.get(s.devId) != s) return;
            Device d = store.find(s.devId);
            if (d == null) return;
            d.state = state;
            if (state == Device.ONLINE) {
                d.error = null;
                String v = s.workingVersion();
                if (v != null && !v.equals(d.version)) {
                    d.version = v;
                    signatures.put(d.id, d.ip + "|" + d.key + "|" + d.version);
                    store.save();
                }
                // Un subdispositivo que no contesta por el hub no debe quedarse «conectando» para siempre.
                main.postDelayed(() -> {
                    if (sessions.get(s.devId) != s || s.state() != TuyaSession.ONLINE) return;
                    boolean any = false;
                    for (Device c : store.childrenOf(s.devId)) {
                        if (c.state != Device.ONLINE) { c.state = Device.OFFLINE; c.error = "hijo_mudo"; any = true; }
                    }
                    if (any) changed();
                }, 6000);
            } else {
                if (error != null) d.error = error;
                for (Device c : store.childrenOf(d.id)) {
                    c.state = state == Device.CONNECTING && c.state == Device.OFFLINE ? Device.CONNECTING : state;
                    c.error = state == Device.OFFLINE ? "hub_fuera" : null;
                }
            }
            changed();
        });
    }

    @Override public void onDps(final TuyaSession s, final String cid, final Map<String, Object> dps) {
        main.post(() -> {
            if (sessions.get(s.devId) != s) return;
            Device d = null;
            if (cid == null) {
                d = store.find(s.devId);
            } else {
                for (Device c : store.childrenOf(s.devId)) if (c.cid.equals(cid)) d = c;
            }
            if (d == null) return;
            for (Map.Entry<String, Object> e : dps.entrySet()) {
                if (e.getValue() != null) d.values.put(e.getKey(), e.getValue());
            }
            if (d.isSub()) { d.state = Device.ONLINE; d.error = null; }
            if (DpLogic.learn(d, dps)) store.save();
            changed();
        });
    }

    @Override public void onFound(final Discovery.Found f) {
        main.post(() -> {
            Discovery.Found old = found.get(f.id);
            boolean isNew = old == null || !eq(old.ip, f.ip) || !eq(old.version, f.version);
            found.put(f.id, f);
            Device d = store.find(f.id);
            if (d != null && !d.isSub() && f.ip != null) {
                boolean ch = false;
                if (!f.ip.equals(d.ip)) { d.ip = f.ip; ch = true; }
                // El anuncio del propio dispositivo manda: así se sigue un cambio de firmware.
                if (TuyaSession.isVersion(f.version) && !f.version.equals(d.version)) { d.version = f.version; ch = true; }
                if (ch) {
                    store.save();
                    sync();
                }
            }
            if (isNew) changed();
        });
    }

    private static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }
}
