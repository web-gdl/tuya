package app.hogarlocal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.hogarlocal.model.Device;
import app.hogarlocal.model.Dp;
import app.hogarlocal.model.DpLogic;
import app.hogarlocal.proto.TuyaSession;
import app.hogarlocal.sched.Planner;
import app.hogarlocal.sched.Runner;
import app.hogarlocal.sched.Schedule;

/**
 * Único componente que corre sin la app en pantalla, y solo cuando hay reglas:
 * a la hora programada (y al reiniciar el teléfono o cambiar la hora, para reponer la alarma).
 */
public final class ScheduleReceiver extends BroadcastReceiver {
    private static final int PORT = 6668;
    /** Si el teléfono despertó más tarde que esto, la orden ya no se manda. */
    private static final long MAX_LATE_MS = 30 * 60 * 1000L;
    private static final long RUN_TIMEOUT_MS = 15000;

    public static final String LATE = "tarde", NO_DEVICE = "sin_dispositivo", NO_KEY = "sin_clave";

    @Override public void onReceive(Context context, Intent intent) {
        final Context c = context.getApplicationContext();
        final Home home = Home.get(c);
        if (!Scheduler.ACTION.equals(intent.getAction())) {
            // Arranque, cambio de hora o actualización de la app: solo reponer la alarma.
            Scheduler.update(c);
            return;
        }
        final long at = intent.getLongExtra(Scheduler.EXTRA_AT, 0);
        final long now = System.currentTimeMillis();
        List<Schedule> due = at > 0
                ? Planner.due(home.store.schedules, ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault()))
                : new ArrayList<>();
        // La siguiente alarma se pone ya, antes de que algo pueda fallar.
        Scheduler.update(c);
        if (due.isEmpty()) return;

        if (now - at > MAX_LATE_MS) {
            for (Schedule s : due) mark(s, LATE, now);
            home.save();
            home.changed();
            return;
        }

        // Órdenes agrupadas por conexión (un hub lleva las de todos sus subdispositivos).
        final Map<String, Runner.Target> targets = new LinkedHashMap<>();
        final Map<Runner.Command, Schedule> owners = new HashMap<>();
        for (Schedule s : due) {
            Device d = home.store.find(s.deviceId);
            if (d == null) { mark(s, NO_DEVICE, now); continue; }
            List<String> ids = new ArrayList<>();
            List<Dp> sw = DpLogic.switches(d);
            for (Dp p : sw) if (s.dps.isEmpty() || s.dps.contains(p.id)) ids.add(p.id);
            if (ids.isEmpty() && sw.isEmpty()) ids.add("1");
            Map<String, Object> dps = Runner.setAll(ids, s.on);
            if (home.isLive(d)) {
                // La app está abierta y conectada: se usa esa conexión.
                mark(s, home.control(d, dps) ? Runner.UNCONFIRMED : TuyaSession.ERR_NO_REPLY, now);
                continue;
            }
            Device conn = d.isSub() ? home.store.find(d.parent) : d;
            if (conn == null || !Home.validKey(conn.key)) { mark(s, NO_KEY, now); continue; }
            if (conn.ip.isEmpty()) { mark(s, "sin_ip", now); continue; }
            Runner.Target t = targets.get(conn.id);
            if (t == null) {
                t = new Runner.Target(conn.id, conn.ip, PORT, conn.key, conn.version);
                targets.put(conn.id, t);
            }
            Runner.Command cmd = new Runner.Command(d.isSub() ? d.cid : null, dps);
            t.commands.add(cmd);
            owners.put(cmd, s);
        }
        home.save();
        home.changed();
        if (targets.isEmpty()) return;

        final PendingResult pending = goAsync();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                Runner.run(new ArrayList<>(targets.values()), RUN_TIMEOUT_MS);
            } finally {
                main.post(() -> {
                    long done = System.currentTimeMillis();
                    for (Runner.Target t : targets.values()) {
                        for (Runner.Command cmd : t.commands) {
                            Schedule s = owners.get(cmd);
                            // La regla puede haberse borrado mientras tanto.
                            if (s != null && home.store.schedule(s.id) == s) mark(s, cmd.result, done);
                        }
                        Device conn = home.store.find(t.id);
                        String v = t.workingVersion;
                        if (conn != null && v != null && !v.equals(conn.version)) conn.version = v;
                    }
                    home.save();
                    home.changed();
                    // Se espera a que el archivo quede escrito antes de soltar el receptor.
                    new Thread(() -> {
                        try {
                            home.store.flush();
                        } catch (Exception ignored) {
                        } finally {
                            pending.finish();
                        }
                    }, "programacion-fin").start();
                });
            }
        }, "programacion").start();
    }

    private static void mark(Schedule s, String result, long when) {
        s.lastRun = when;
        s.lastResult = result;
    }
}
