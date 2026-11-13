package app.hogarlocal.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import app.hogarlocal.Home;
import app.hogarlocal.R;
import app.hogarlocal.ScheduleReceiver;
import app.hogarlocal.Scheduler;
import app.hogarlocal.model.Device;
import app.hogarlocal.model.Dp;
import app.hogarlocal.model.DpLogic;
import app.hogarlocal.proto.TuyaSession;
import app.hogarlocal.sched.Planner;
import app.hogarlocal.sched.Runner;
import app.hogarlocal.sched.Schedule;

/** Programación semanal de encendido y apagado; de un dispositivo o de todo el hogar. */
public final class ScheduleActivity extends Activity implements Home.Observer {
    private static final String[] DAY_LETTERS = {"L", "M", "M", "J", "V", "S", "D"};
    private static final String[] DAY_NAMES = {"lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};

    private Home home;
    /** null = todas las reglas del hogar. */
    private Device device;
    private LinearLayout body;
    private String signature = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        home = Home.get(this);
        String id = getIntent().getStringExtra("id");
        if (id != null) {
            device = home.store.find(id);
            if (device == null) { finish(); return; }
        }
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.color(this, R.color.bg));
        Ui.fitSystemBars(root);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 4));
        ImageView back = Ui.icon(this, R.drawable.ic_back, R.color.text);
        back.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        back.setContentDescription("Atrás");
        Ui.clickable(back, v -> finish());
        bar.addView(back, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));
        LinearLayout tcol = Ui.column(this);
        tcol.setPadding(Ui.dp(this, 4), 0, 0, 0);
        tcol.addView(Ui.oneLine(Ui.text(this, "Programación", 19, R.color.text, true)));
        if (device != null) tcol.addView(Ui.oneLine(Ui.text(this, device.name, 13, R.color.text2, false)));
        bar.addView(tcol, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageView add = Ui.roundButton(this, R.drawable.ic_add, 40, "Agregar horario");
        add.setBackground(Ui.pressable(this, Ui.color(this, R.color.accent), 20));
        add.setImageTintList(ColorStateList.valueOf(Ui.color(this, R.color.on_accent)));
        add.setOnClickListener(v -> addNew());
        bar.addView(add, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        body = Ui.column(this);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(body);
        root.addView(scroll, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    @Override protected void onStart() {
        super.onStart();
        if (body == null) return;
        home.addObserver(this);
        signature = "";
        refresh();
    }

    @Override protected void onStop() {
        super.onStop();
        if (body == null) return;
        home.removeObserver(this);
    }

    @Override public void onHomeChanged() {
        if (device != null && home.store.find(device.id) != device) { finish(); return; }
        refresh();
    }

    private List<Schedule> list() {
        List<Schedule> out = device != null ? home.store.schedulesOf(device.id) : new ArrayList<>(home.store.schedules);
        // En la vista del hogar se agrupan por dispositivo (en el orden de la pantalla de inicio).
        out.sort((a, b) -> {
            int c = device != null ? 0 : Integer.compare(position(a.deviceId), position(b.deviceId));
            if (c == 0) c = Integer.compare(a.hour * 60 + a.minute, b.hour * 60 + b.minute);
            return c != 0 ? c : a.id.compareTo(b.id);
        });
        return out;
    }

    private int position(String deviceId) {
        for (int i = 0; i < home.store.devices.size(); i++) if (home.store.devices.get(i).id.equals(deviceId)) return i;
        return Integer.MAX_VALUE;
    }

    // ---- lista ----

    private void refresh() {
        List<Schedule> items = list();
        StringBuilder sig = new StringBuilder();
        sig.append(Scheduler.exactAllowed(this)).append(Scheduler.batteryRestricted(this));
        for (Schedule s : items) {
            sig.append(s.id).append(s.hour).append(s.minute).append(s.days).append(s.on).append(s.enabled)
                    .append(s.dps).append(s.lastRun).append(s.lastResult).append(';');
        }
        if (sig.toString().equals(signature)) return;
        signature = sig.toString();
        body.removeAllViews();

        body.addView(hint());
        if (items.isEmpty()) {
            TextView t = Ui.text(this, device != null
                    ? "Sin horarios. Toca + para encender o apagar este dispositivo a una hora fija, los días que elijas."
                    : "Sin horarios. Toca + para programar el encendido o apagado semanal de tus dispositivos.",
                    15, R.color.text2, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(this, 16), Ui.dp(this, 40), Ui.dp(this, 16), 0);
            body.addView(t);
            return;
        }
        String lastDevice = null;
        LinearLayout card = null;
        for (Schedule s : items) {
            Device d = home.store.find(s.deviceId);
            if (d == null) continue;
            if (device == null && !d.id.equals(lastDevice)) {
                // En la vista de todo el hogar se agrupan por dispositivo, en orden de hora.
                lastDevice = d.id;
                TextView h = Ui.text(this, d.name, 13, R.color.text2, true);
                h.setPadding(Ui.dp(this, 4), Ui.dp(this, 14), 0, Ui.dp(this, 8));
                body.addView(h);
                card = null;
            }
            if (card == null) {
                card = Ui.card(this);
                LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (device != null) lp.topMargin = Ui.dp(this, 12);
                body.addView(card, lp);
            } else {
                card.addView(Ui.divider(this));
            }
            card.addView(row(s, d));
        }
    }

    private View hint() {
        LinearLayout col = Ui.column(this);
        String text;
        boolean warn = false;
        if (!Scheduler.exactAllowed(this)) {
            text = "Android no permite alarmas exactas a esta app: los horarios pueden retrasarse varios minutos. Toca para permitirlas.";
            warn = true;
        } else if (Scheduler.batteryRestricted(this)) {
            text = "Para que se cumplan con la pantalla apagada, quita el ahorro de batería de la app (Batería → Sin restricciones; en Xiaomi activa también «Inicio automático»). Toca para abrir los ajustes.";
            warn = true;
        } else {
            text = "Los horarios los cumple este teléfono: a la hora indicada se conecta al dispositivo por tu Wi-Fi. Debe estar encendido y en la red de casa.";
        }
        TextView t = Ui.text(this, text, 13, warn ? R.color.text : R.color.text2, false);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        t.setBackground(Ui.round(Ui.color(this, warn ? R.color.chip : R.color.bg), Ui.dp(this, 14)));
        col.addView(t);
        if (warn) {
            final boolean exact = !Scheduler.exactAllowed(this);
            t.setOnClickListener(v -> openSettings(exact));
        }
        return col;
    }

    private void openSettings(boolean exact) {
        Intent i;
        if (exact && android.os.Build.VERSION.SDK_INT >= 31) {
            i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()));
        } else {
            i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
        }
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            Actions.toast(this, "Abre Ajustes → Apps → Hogar Local");
        }
    }

    private String time(int h, int m) {
        if (DateFormat.is24HourFormat(this)) return String.format("%02d:%02d", h, m);
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format("%d:%02d %s", h12, m, h < 12 ? "a. m." : "p. m.");
    }

    private String keysText(Schedule s, Device d) {
        List<Dp> sw = DpLogic.switches(d);
        if (sw.size() <= 1 || s.dps.isEmpty() || s.dps.size() >= sw.size()) return "";
        List<String> names = new ArrayList<>();
        for (int i = 0; i < sw.size(); i++) if (s.dps.contains(sw.get(i).id)) names.add(DpLogic.label(sw.get(i), i, sw.size()));
        return " · " + String.join(", ", names);
    }

    static String resultText(String r) {
        switch (r) {
            case Runner.OK: return "cumplido";
            case Runner.UNCONFIRMED: return "enviado, sin confirmar";
            case ScheduleReceiver.LATE: return "no se envió: el teléfono despertó tarde";
            case ScheduleReceiver.NO_KEY: return "falló: falta la clave local";
            case ScheduleReceiver.NO_DEVICE: return "falló: el dispositivo ya no existe";
            case "sin_ip": return "falló: no se conoce su IP";
            case TuyaSession.ERR_KEY: return "falló: clave local incorrecta";
            case TuyaSession.ERR_NO_REPLY: return "falló: no respondió";
            case TuyaSession.ERR_UNREACHABLE: return "falló: sin conexión (¿el teléfono estaba fuera de casa?)";
            default: return "falló";
        }
    }

    private String whenText(long millis) {
        ZonedDateTime t = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault());
        LocalDate today = LocalDate.now();
        String day;
        if (t.toLocalDate().equals(today)) day = "hoy";
        else if (t.toLocalDate().equals(today.minusDays(1))) day = "ayer";
        else if (t.toLocalDate().equals(today.plusDays(1))) day = "mañana";
        else day = "el " + DAY_NAMES[t.getDayOfWeek().getValue() - 1] + " " + t.getDayOfMonth();
        return day + " " + time(t.getHour(), t.getMinute());
    }

    private View row(final Schedule s, final Device d) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        r.setMinimumHeight(Ui.dp(this, 76));
        LinearLayout col = Ui.column(this);
        TextView t = Ui.text(this, time(s.hour, s.minute), 26, R.color.text, true);
        t.setAlpha(s.enabled ? 1f : 0.45f);
        col.addView(t);
        TextView what = Ui.text(this, (s.on ? "Encender" : "Apagar") + " · " + Schedule.daysText(s.days) + keysText(s, d), 14,
                s.on ? R.color.accent : R.color.text2, false);
        what.setAlpha(s.enabled ? 1f : 0.6f);
        col.addView(what);
        String extra;
        if (!s.enabled) extra = "Desactivado";
        else {
            ZonedDateTime next = Planner.next(s, ZonedDateTime.now());
            extra = next == null ? "" : "Próxima: " + whenText(next.toInstant().toEpochMilli());
        }
        if (s.lastRun > 0) extra += (extra.isEmpty() ? "" : "\n") + "Última: " + whenText(s.lastRun) + " · " + resultText(s.lastResult);
        if (!extra.isEmpty()) {
            TextView x = Ui.text(this, extra, 12, R.color.text2, false);
            x.setPadding(0, Ui.dp(this, 3), 0, 0);
            col.addView(x);
        }
        r.addView(col, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch sw = new Switch(this);
        sw.setChecked(s.enabled);
        sw.setContentDescription("Activar horario");
        sw.setOnCheckedChangeListener((v, checked) -> {
            if (s.enabled == checked) return;
            s.enabled = checked;
            saved();
        });
        r.addView(sw);
        Ui.clickable(r, v -> edit(s, d, false));
        r.setOnLongClickListener(v -> { confirmDelete(s); return true; });
        return r;
    }

    private void saved() {
        home.save();
        Scheduler.update(this);
        home.changed();
    }

    private void confirmDelete(final Schedule s) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar horario")
                .setMessage(time(s.hour, s.minute) + " · " + (s.on ? "Encender" : "Apagar") + " · " + Schedule.daysText(s.days))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (x, w) -> {
                    home.store.schedules.remove(s);
                    saved();
                })
                .show();
    }

    // ---- alta y edición ----

    private void addNew() {
        if (device != null) { newFor(device); return; }
        final List<Device> options = new ArrayList<>();
        for (Device d : home.store.devices) if (!DpLogic.switches(d).isEmpty()) options.add(d);
        if (options.isEmpty()) {
            Actions.toast(this, "Aún no hay dispositivos con interruptores");
            return;
        }
        String[] names = new String[options.size()];
        for (int i = 0; i < names.length; i++) {
            Device d = options.get(i);
            names[i] = d.room.isEmpty() ? d.name : d.name + " · " + d.room;
        }
        new AlertDialog.Builder(this)
                .setTitle("¿Qué dispositivo?")
                .setItems(names, (x, which) -> newFor(options.get(which)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void newFor(Device d) {
        Schedule s = new Schedule();
        s.id = Schedule.newId();
        s.deviceId = d.id;
        Calendar c = Calendar.getInstance();
        c.add(Calendar.HOUR_OF_DAY, 1);
        s.hour = c.get(Calendar.HOUR_OF_DAY);
        s.minute = 0;
        edit(s, d, true);
    }

    /** Editor: hora, acción, días y teclas. Trabaja sobre una copia hasta pulsar «Guardar». */
    private void edit(final Schedule original, final Device d, final boolean isNew) {
        final int[] hm = {original.hour, original.minute};
        final boolean[] on = {original.on};
        final int[] days = {original.days};
        final List<Dp> sw = DpLogic.switches(d);

        LinearLayout col = Ui.column(this);
        final TextView clock = Ui.text(this, time(hm[0], hm[1]), 44, R.color.text, true);
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        clock.setBackground(Ui.pressable(this, Ui.color(this, R.color.chip), 16));
        clock.setContentDescription("Cambiar hora");
        clock.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, m) -> {
            hm[0] = h;
            hm[1] = m;
            clock.setText(time(h, m));
        }, hm[0], hm[1], DateFormat.is24HourFormat(this)).show());
        col.addView(clock, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 76)));

        col.addView(Actions.label(this, "Acción"));
        LinearLayout act = Ui.row(this);
        final TextView bOn = pill("Encender"), bOff = pill("Apagar");
        Runnable paintAct = () -> {
            paintPill(bOn, on[0]);
            paintPill(bOff, !on[0]);
        };
        bOn.setOnClickListener(v -> { on[0] = true; paintAct.run(); });
        bOff.setOnClickListener(v -> { on[0] = false; paintAct.run(); });
        LinearLayout.LayoutParams lp1 = Ui.lp(0, Ui.dp(this, 42), 1);
        LinearLayout.LayoutParams lp2 = Ui.lp(0, Ui.dp(this, 42), 1);
        lp2.leftMargin = Ui.dp(this, 10);
        act.addView(bOn, lp1);
        act.addView(bOff, lp2);
        paintAct.run();
        col.addView(act);

        col.addView(Actions.label(this, "Días"));
        LinearLayout dayRow = Ui.row(this);
        final List<TextView> dayViews = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            TextView t = pill(DAY_LETTERS[i]);
            t.setContentDescription(DAY_NAMES[i]);
            t.setOnClickListener(v -> {
                days[0] ^= bit;
                paintPill((TextView) v, (days[0] & bit) != 0);
            });
            paintPill(t, (days[0] & bit) != 0);
            LinearLayout.LayoutParams lp = Ui.lp(0, Ui.dp(this, 40), 1);
            if (i > 0) lp.leftMargin = Ui.dp(this, 5);
            dayRow.addView(t, lp);
            dayViews.add(t);
        }
        col.addView(dayRow);
        LinearLayout quick = Ui.row(this);
        quick.setPadding(0, Ui.dp(this, 6), 0, 0);
        int[][] presets = {{Schedule.EVERY_DAY}, {Schedule.WEEKDAYS}, {Schedule.WEEKEND}};
        String[] presetNames = {"Todos", "Lun–Vie", "Sáb–Dom"};
        for (int k = 0; k < 3; k++) {
            final int mask = presets[k][0];
            TextView q = Ui.text(this, presetNames[k], 13, R.color.accent, true);
            q.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
            q.setOnClickListener(v -> {
                days[0] = mask;
                for (int i = 0; i < 7; i++) paintPill(dayViews.get(i), (mask & (1 << i)) != 0);
            });
            quick.addView(q);
        }
        col.addView(quick);

        final List<CheckBox> checks = new ArrayList<>();
        if (sw.size() > 1) {
            col.addView(Actions.label(this, "Teclas"));
            for (int i = 0; i < sw.size(); i++) {
                CheckBox cb = new CheckBox(this);
                cb.setText(DpLogic.label(sw.get(i), i, sw.size()));
                cb.setTextColor(Ui.color(this, R.color.text));
                cb.setChecked(original.dps.isEmpty() || original.dps.contains(sw.get(i).id));
                col.addView(cb);
                checks.add(cb);
            }
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(isNew ? "Nuevo horario · " + d.name : "Horario · " + d.name)
                .setView(wrap(col))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null);
        if (!isNew) b.setNeutralButton("Eliminar", (x, w) -> confirmDelete(original));
        final AlertDialog dlg = b.create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if ((days[0] & Schedule.EVERY_DAY) == 0) { Actions.toast(this, "Elige al menos un día"); return; }
            List<String> chosen = new ArrayList<>();
            for (int i = 0; i < checks.size(); i++) if (checks.get(i).isChecked()) chosen.add(sw.get(i).id);
            if (!checks.isEmpty() && chosen.isEmpty()) { Actions.toast(this, "Elige al menos una tecla"); return; }
            original.hour = hm[0];
            original.minute = hm[1];
            original.on = on[0];
            original.days = days[0];
            original.dps.clear();
            if (chosen.size() < sw.size()) original.dps.addAll(chosen);
            original.enabled = true;
            if (isNew) home.store.schedules.add(original);
            saved();
            dlg.dismiss();
            ZonedDateTime next = Planner.next(original, ZonedDateTime.now());
            if (next != null) Actions.toast(this, "Próxima vez: " + whenText(next.toInstant().toEpochMilli()));
        }));
        dlg.show();
    }

    private View wrap(View content) {
        ScrollView s = new ScrollView(this);
        s.addView(Ui.padded(this, content));
        return s;
    }

    private TextView pill(String text) {
        TextView t = Ui.text(this, text, 14, R.color.text, true);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void paintPill(TextView t, boolean selected) {
        t.setBackground(Ui.pressable(this, Ui.color(this, selected ? R.color.accent : R.color.chip), 21));
        t.setTextColor(Ui.color(this, selected ? R.color.on_accent : R.color.text2));
        t.setSelected(selected);
    }
}
