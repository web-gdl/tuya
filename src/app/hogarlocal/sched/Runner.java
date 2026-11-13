package app.hogarlocal.sched;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import app.hogarlocal.proto.TuyaSession;

/**
 * Ejecuta órdenes con una conexión de un solo uso: conecta, envía, espera la
 * confirmación y cierra. Sin Android: se prueba en la JVM con el dispositivo simulado.
 */
public final class Runner {
    public static final String OK = "ok", UNCONFIRMED = "sin_confirmar";

    /** Una orden: a quién (cid null = el propio equipo) y qué DPs. */
    public static final class Command {
        public final String cid;
        public final Map<String, Object> dps;
        public String result = TuyaSession.ERR_UNREACHABLE;

        public Command(String cid, Map<String, Object> dps) {
            this.cid = cid;
            this.dps = dps;
        }
    }

    /** Conexión a un equipo (o hub) y las órdenes que se le mandan por ella. */
    public static final class Target {
        public final String id, ip, key, version;
        public final int port;
        public final List<Command> commands = new ArrayList<>();
        /** Versión con la que respondió; null si no conectó. */
        public volatile String workingVersion;

        public Target(String id, String ip, int port, String key, String version) {
            this.id = id;
            this.ip = ip;
            this.port = port;
            this.key = key;
            this.version = version;
        }
    }

    private Runner() { }

    /** Ejecuta todos los destinos en paralelo; regresa cuando terminan o vence el plazo. */
    public static void run(List<Target> targets, long timeoutMs) {
        List<Thread> threads = new ArrayList<>();
        for (Target t : targets) {
            Thread th = new Thread(() -> runOne(t, timeoutMs), "prog-" + t.id);
            th.setDaemon(true);
            th.start();
            threads.add(th);
        }
        long end = System.currentTimeMillis() + timeoutMs + 1500;
        for (Thread th : threads) {
            try {
                th.join(Math.max(1, end - System.currentTimeMillis()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static boolean matches(Map<String, Object> have, Map<String, Object> want) {
        if (have == null) return false;
        for (Map.Entry<String, Object> e : want.entrySet()) {
            Object v = have.get(e.getKey());
            if (v == null) return false;
            Object w = e.getValue();
            if (v instanceof Number && w instanceof Number) {
                if (((Number) v).longValue() != ((Number) w).longValue()) return false;
            } else if (!v.equals(w)) {
                return false;
            }
        }
        return true;
    }

    static void runOne(Target t, long timeoutMs) {
        final CountDownLatch online = new CountDownLatch(1);
        final Map<String, Map<String, Object>> seen = new ConcurrentHashMap<>();
        final String[] lastError = {null};
        final String self = "\u0000self";
        TuyaSession s = new TuyaSession(t.id, t.ip, t.port, t.key, t.version, new TuyaSession.Listener() {
            @Override public void onState(TuyaSession x, int state, String error) {
                if (state == TuyaSession.ONLINE) online.countDown();
                if (error != null) lastError[0] = error;
            }

            @Override public void onDps(TuyaSession x, String cid, Map<String, Object> dps) {
                seen.computeIfAbsent(cid == null ? self : cid, k -> new ConcurrentHashMap<>());
                for (Map.Entry<String, Object> e : dps.entrySet()) {
                    if (e.getValue() != null) seen.get(cid == null ? self : cid).put(e.getKey(), e.getValue());
                }
            }
        });
        long end = System.currentTimeMillis() + timeoutMs;
        try {
            s.start();
            boolean up = online.await(Math.max(1, end - System.currentTimeMillis() - 2000), TimeUnit.MILLISECONDS);
            if (!up) {
                String err = lastError[0] != null ? lastError[0] : TuyaSession.ERR_NO_REPLY;
                for (Command c : t.commands) c.result = err;
                return;
            }
            t.workingVersion = s.workingVersion();
            // Lo que ya está en el estado pedido no se reenvía, pero cuenta como cumplido.
            for (Command c : t.commands) {
                c.result = s.control(c.cid, c.dps) ? UNCONFIRMED : TuyaSession.ERR_NO_REPLY;
            }
            long confirmBy = Math.min(end, System.currentTimeMillis() + 4000);
            while (System.currentTimeMillis() < confirmBy) {
                boolean all = true;
                for (Command c : t.commands) {
                    if (c.result.equals(UNCONFIRMED) && matches(seen.get(c.cid == null ? self : c.cid), c.dps)) c.result = OK;
                    if (c.result.equals(UNCONFIRMED)) all = false;
                }
                if (all) break;
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            s.close();
        }
    }

    /** Orden sencilla: poner todas esas teclas en el mismo estado. */
    public static Map<String, Object> setAll(List<String> dpIds, boolean on) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String id : dpIds) m.put(id, on);
        return m;
    }
}
