package app.hogarlocal.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.hogarlocal.Home;
import app.hogarlocal.R;
import app.hogarlocal.model.Device;
import app.hogarlocal.model.Dp;
import app.hogarlocal.model.DpLogic;
import app.hogarlocal.sched.Planner;
import app.hogarlocal.sched.Schedule;

/** Detalle de un dispositivo: encendido, temporizador, consumo y datos de conexión. */
public final class DeviceActivity extends Activity implements Home.Observer {
    private static final long[] PRESETS = {60, 300, 600, 1800, 3600, 7200, 14400};

    private Home home;
    private Device device;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView title, status;
    private LinearLayout body;
    private String structure = "";

    // Vistas que se actualizan sin reconstruir
    private final List<ImageView> powers = new ArrayList<>();
    private final List<TextView> labels = new ArrayList<>();
    private final List<TextView> timers = new ArrayList<>();
    private TextView heroState, watts, volts, amps, roomValue, linkValue, scheduleValue;

    private final Runnable metricsTick = new Runnable() {
        @Override public void run() {
            if (device != null) home.refreshMetrics(device);
            handler.postDelayed(this, 5000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        home = Home.get(this);
        device = home.store.find(String.valueOf(getIntent().getStringExtra("id")));
        if (device == null) { finish(); return; }

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.color(this, R.color.bg));
        Ui.fitSystemBars(root);
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 4));
        ImageView back = Ui.icon(this, R.drawable.ic_back, R.color.text);
        back.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        back.setContentDescription("Atrás");
        Ui.clickable(back, v -> finish());
        bar.addView(back, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));
        LinearLayout tcol = Ui.column(this);
        tcol.setPadding(Ui.dp(this, 4), 0, 0, 0);
        title = Ui.oneLine(Ui.text(this, "", 19, R.color.text, true));
        status = Ui.oneLine(Ui.text(this, "", 13, R.color.text2, false));
        tcol.addView(title);
        tcol.addView(status);
        bar.addView(tcol, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageView more = Ui.icon(this, R.drawable.ic_more, R.color.text);
        more.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        more.setContentDescription("Más opciones");
        Ui.clickable(more, this::menu);
        bar.addView(more, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = Ui.column(this);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(body);
        root.addView(scroll, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        refresh();
    }

    @Override protected void onStart() {
        super.onStart();
        if (device == null) return;
        home.addObserver(this);
        home.acquire();
        handler.postDelayed(metricsTick, 1500);
        refresh();
    }

    @Override protected void onStop() {
        super.onStop();
        if (device == null) return;
        handler.removeCallbacks(metricsTick);
        home.removeObserver(this);
        home.release();
    }

    @Override public void onHomeChanged() {
        if (home.store.find(device.id) != device) { finish(); return; }
        refresh();
    }

    private void menu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Cambiar nombre");
        m.getMenu().add(0, 2, 1, "Mover a cuarto");
        m.getMenu().add(0, 3, 2, "Conexión");
        m.getMenu().add(0, 4, 3, "Eliminar");
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: Actions.rename(this, home, device); break;
                case 2: Actions.pickRoom(this, home, device); break;
                case 3: Actions.connection(this, home, device, null, null, null); break;
                default: Actions.confirmDelete(this, home, device, this::finish);
            }
            return true;
        });
        m.show();
    }

    // ---- contenido ----

    private void refresh() {
        if (body == null) return;
        List<Dp> sw = DpLogic.switches(device);
        StringBuilder sig = new StringBuilder();
        for (Dp s : sw) sig.append(s.id).append(DpLogic.countdownFor(device, s) != null ? "t" : "").append(',');
        sig.append(DpLogic.metricIds(device).isEmpty() ? "" : "m").append(home.store.childrenOf(device.id).size());
        if (!sig.toString().equals(structure)) {
            structure = sig.toString();
            rebuild(sw);
        }
        bind(sw);
    }

    private void rebuild(List<Dp> sw) {
        body.removeAllViews();
        powers.clear();
        labels.clear();
        timers.clear();
        heroState = watts = volts = amps = null;

        if (sw.size() == 1) {
            buildHero(sw.get(0));
        } else if (sw.size() > 1) {
            buildAllButtons(sw);
            LinearLayout card = Ui.card(this);
            for (int i = 0; i < sw.size(); i++) {
                if (i > 0) card.addView(Ui.divider(this));
                card.addView(buildSwitchRow(sw.get(i)));
            }
            body.addView(card);
        } else {
            int kids = home.store.childrenOf(device.id).size();
            TextView t = Ui.text(this, kids > 0
                    ? "Este es un hub. Sus " + kids + " dispositivo(s) aparecen en la pantalla de inicio y se controlan a través de él."
                    : "Todavía no se conocen los interruptores de este dispositivo. Aparecerán en cuanto se conecte y envíe su estado.",
                    15, R.color.text2, false);
            t.setPadding(Ui.dp(this, 8), Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24));
            t.setGravity(Gravity.CENTER);
            body.addView(t);
        }

        if (!DpLogic.metricIds(device).isEmpty()) {
            body.addView(Ui.space(this, 1, 16));
            LinearLayout card = Ui.card(this);
            LinearLayout r = Ui.row(this);
            r.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
            watts = metric(r, "Potencia");
            volts = metric(r, "Voltaje");
            amps = metric(r, "Corriente");
            card.addView(r);
            body.addView(card);
        }

        body.addView(Ui.space(this, 1, 16));
        LinearLayout info = Ui.card(this);
        scheduleValue = null;
        if (!sw.isEmpty()) {
            LinearLayout schedRow = Ui.listRow(this, "Programación", " ", true);
            scheduleValue = (TextView) ((LinearLayout) schedRow.getChildAt(0)).getChildAt(1);
            Ui.clickable(schedRow, v -> startActivity(new Intent(this, ScheduleActivity.class).putExtra("id", device.id)));
            info.addView(schedRow);
            info.addView(Ui.divider(this));
        }
        LinearLayout roomRow = Ui.listRow(this, "Cuarto", " ", true);
        roomValue = (TextView) ((LinearLayout) roomRow.getChildAt(0)).getChildAt(1);
        Ui.clickable(roomRow, v -> Actions.pickRoom(this, home, device));
        info.addView(roomRow);
        info.addView(Ui.divider(this));
        LinearLayout linkRow = Ui.listRow(this, "Conexión", " ", true);
        linkValue = (TextView) ((LinearLayout) linkRow.getChildAt(0)).getChildAt(1);
        Ui.clickable(linkRow, v -> Actions.connection(this, home, device, null, null, null));
        info.addView(linkRow);
        info.addView(Ui.divider(this));
        info.addView(Ui.listRow(this, "Identificador", device.id, false));
        body.addView(info);
    }

    private TextView metric(LinearLayout row, String label) {
        LinearLayout col = Ui.column(this);
        col.setGravity(Gravity.CENTER);
        TextView v = Ui.text(this, "—", 20, R.color.text, true);
        v.setGravity(Gravity.CENTER);
        TextView l = Ui.text(this, label, 12, R.color.text2, false);
        l.setGravity(Gravity.CENTER);
        col.addView(v);
        col.addView(l);
        row.addView(col, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return v;
    }

    private void buildHero(final Dp sw) {
        LinearLayout col = Ui.column(this);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, Ui.dp(this, 36), 0, Ui.dp(this, 28));
        ImageView p = Ui.roundButton(this, R.drawable.ic_power, 168, "Encender o apagar");
        int pad = Ui.dp(this, 52);
        p.setPadding(pad, pad, pad, pad);
        p.setOnClickListener(v -> toggle(v, sw));
        powers.add(p);
        col.addView(p, Ui.lp(Ui.dp(this, 168), Ui.dp(this, 168)));
        heroState = Ui.text(this, "", 20, R.color.text, true);
        heroState.setPadding(0, Ui.dp(this, 18), 0, 0);
        col.addView(heroState);
        labels.add(null);
        final Dp cd = DpLogic.countdownFor(device, sw);
        if (cd != null) {
            TextView t = Ui.text(this, "Temporizador", 14, R.color.text, false);
            t.setGravity(Gravity.CENTER);
            t.setCompoundDrawablePadding(Ui.dp(this, 8));
            t.setBackground(Ui.pressable(this, Ui.color(this, R.color.card), 22));
            t.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
            t.setOnClickListener(v -> timerDialog(sw, cd));
            LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44));
            lp.topMargin = Ui.dp(this, 18);
            col.addView(t, lp);
            timers.add(t);
        } else {
            timers.add(null);
        }
        body.addView(col);
    }

    private void buildAllButtons(final List<Dp> sw) {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 16));
        for (int k = 0; k < 2; k++) {
            final boolean on = k == 0;
            TextView t = Ui.text(this, on ? "Encender todo" : "Apagar todo", 14, R.color.text, true);
            t.setGravity(Gravity.CENTER);
            t.setBackground(Ui.pressable(this, Ui.color(this, R.color.card), 22));
            t.setOnClickListener(v -> {
                Map<String, Object> m = new LinkedHashMap<>();
                for (Dp s : sw) m.put(s.id, on);
                send(v, m);
            });
            LinearLayout.LayoutParams lp = Ui.lp(0, Ui.dp(this, 44), 1);
            if (k == 1) lp.leftMargin = Ui.dp(this, 12);
            r.addView(t, lp);
        }
        body.addView(r);
    }

    private View buildSwitchRow(final Dp sw) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        r.setMinimumHeight(Ui.dp(this, 68));
        LinearLayout col = Ui.column(this);
        TextView name = Ui.oneLine(Ui.text(this, "", 16, R.color.text, false));
        TextView timer = Ui.text(this, "", 12, R.color.accent, false);
        col.addView(name);
        col.addView(timer);
        col.setOnClickListener(v -> renameSwitch(sw));
        r.addView(col, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        labels.add(name);
        timers.add(timer);
        final Dp cd = DpLogic.countdownFor(device, sw);
        if (cd != null) {
            ImageView tb = Ui.roundButton(this, R.drawable.ic_timer, 40, "Temporizador");
            tb.setOnClickListener(v -> timerDialog(sw, cd));
            LinearLayout.LayoutParams lp = Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40));
            lp.rightMargin = Ui.dp(this, 10);
            r.addView(tb, lp);
        }
        ImageView p = Ui.roundButton(this, R.drawable.ic_power, 46, "Encender o apagar");
        p.setOnClickListener(v -> toggle(v, sw));
        powers.add(p);
        r.addView(p, Ui.lp(Ui.dp(this, 46), Ui.dp(this, 46)));
        return r;
    }

    private void bind(List<Dp> sw) {
        boolean online = device.state == Device.ONLINE;
        title.setText(device.name);
        status.setText(online ? "En línea · red local" : device.state == Device.CONNECTING ? "Conectando…" : Ui.errorText(device));
        for (int i = 0; i < sw.size() && i < powers.size(); i++) {
            Dp s = sw.get(i);
            boolean on = device.isOn(s);
            Ui.paintPower(powers.get(i), on, online, sw.size() == 1 ? 168 : 46);
            Dp cd = DpLogic.countdownFor(device, s);
            Object left = cd == null ? null : device.values.get(cd.id);
            long secs = left instanceof Number ? ((Number) left).longValue() : 0;
            String timerText = secs > 0 ? (on ? "Se apaga en " : "Se enciende en ") + Ui.duration(secs) : "";
            if (sw.size() == 1) {
                heroState.setText(!online ? "Sin conexión" : on ? "Encendido" : "Apagado");
                TextView t = timers.get(0);
                if (t != null) t.setText(secs > 0 ? timerText : "Temporizador");
            } else {
                labels.get(i).setText(DpLogic.label(s, i, sw.size()));
                TextView t = timers.get(i);
                t.setText(timerText);
                t.setVisibility(timerText.isEmpty() ? View.GONE : View.VISIBLE);
            }
        }
        if (watts != null) {
            Double w = DpLogic.watts(device), v = DpLogic.volts(device), a = DpLogic.milliamps(device);
            watts.setText(w == null ? "—" : String.format(Locale.US, "%.1f W", w));
            volts.setText(v == null ? "—" : String.format(Locale.US, "%.1f V", v));
            amps.setText(a == null ? "—" : a >= 1000 ? String.format(Locale.US, "%.2f A", a / 1000) : String.format(Locale.US, "%.0f mA", a));
        }
        if (scheduleValue != null) scheduleValue.setText(scheduleSummary());
        if (roomValue != null) roomValue.setText(device.room.isEmpty() ? "Sin cuarto" : device.room);
        if (linkValue != null) {
            String link;
            if (device.isSub()) {
                Device hub = home.store.find(device.parent);
                link = "Zigbee por " + (hub == null ? "un hub que falta" : "«" + hub.name + "»");
            } else {
                link = (device.ip.isEmpty() ? "IP sin detectar" : device.ip) + " · protocolo "
                        + ("auto".equals(device.version) ? "automático" : device.version);
            }
            linkValue.setText(link);
        }
    }

    private String scheduleSummary() {
        List<Schedule> mine = home.store.schedulesOf(device.id);
        int active = 0;
        for (Schedule s : mine) if (s.enabled) active++;
        if (mine.isEmpty()) return "Encender o apagar a una hora fija";
        if (active == 0) return mine.size() + (mine.size() == 1 ? " horario, desactivado" : " horarios, desactivados");
        ZonedDateTime next = Planner.nextFor(home.store.schedules, device.id, ZonedDateTime.now());
        String n = active + (active == 1 ? " activo" : " activos");
        if (next == null) return n;
        Schedule which = null;
        for (Schedule s : mine) {
            ZonedDateTime t = s.enabled ? Planner.next(s, ZonedDateTime.now()) : null;
            if (t != null && t.equals(next)) which = s;
        }
        String day = next.toLocalDate().equals(java.time.LocalDate.now()) ? "hoy"
                : next.toLocalDate().equals(java.time.LocalDate.now().plusDays(1)) ? "mañana"
                : new String[]{"lun", "mar", "mié", "jue", "vie", "sáb", "dom"}[next.getDayOfWeek().getValue() - 1];
        String hhmm = android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date.from(next.toInstant()));
        return n + " · " + (which != null && !which.on ? "se apaga " : "se enciende ") + day + " " + hhmm;
    }

    // ---- acciones ----

    private void send(View v, Map<String, Object> dps) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (!home.control(device, dps)) Actions.toast(this, "El dispositivo no está conectado");
    }

    private void toggle(View v, Dp sw) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(sw.id, !device.isOn(sw));
        send(v, m);
    }

    private void renameSwitch(final Dp sw) {
        Ui.prompt(this, "Nombre del interruptor", "Techo, Lámpara, Ventilador…", sw.name, s -> {
            sw.name = s;
            home.save();
            home.changed();
        });
    }

    private void timerDialog(final Dp sw, final Dp cd) {
        boolean on = device.isOn(sw);
        Object left = device.values.get(cd.id);
        final boolean running = left instanceof Number && ((Number) left).longValue() > 0;
        final List<String> items = new ArrayList<>();
        if (running) items.add("Cancelar temporizador");
        for (long p : PRESETS) items.add(Ui.duration(p));
        items.add("Otro…");
        new AlertDialog.Builder(this)
                .setTitle(on ? "Apagar en…" : "Encender en…")
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    int i = running ? which - 1 : which;
                    if (i < 0) setTimer(cd, 0);
                    else if (i < PRESETS.length) setTimer(cd, PRESETS[i]);
                    else customTimer(cd);
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void customTimer(final Dp cd) {
        final EditText e = Ui.field(this, "Minutos", "");
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
                .setTitle("Temporizador en minutos")
                .setView(Ui.padded(this, e))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Iniciar", (d, w) -> {
                    try {
                        long min = Long.parseLong(e.getText().toString().trim());
                        if (min < 1 || min > 1440) throw new NumberFormatException();
                        setTimer(cd, min * 60);
                    } catch (NumberFormatException x) {
                        Actions.toast(this, "Escribe entre 1 y 1440 minutos");
                    }
                })
                .show();
    }

    private void setTimer(Dp cd, long seconds) {
        if (!home.set(device, cd.id, seconds)) Actions.toast(this, "El dispositivo no está conectado");
        else Actions.toast(this, seconds == 0 ? "Temporizador cancelado" : "Temporizador de " + Ui.duration(seconds));
    }
}
