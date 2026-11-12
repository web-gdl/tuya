package app.hogarlocal.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.hogarlocal.Home;
import app.hogarlocal.R;
import app.hogarlocal.model.Device;
import app.hogarlocal.model.Dp;
import app.hogarlocal.model.DpLogic;
import app.hogarlocal.proto.Json;

/** Pantalla principal: hogar, cuartos y tarjetas de dispositivos; segunda pestaña con ajustes. */
public final class MainActivity extends Activity implements Home.Observer {
    private static final int REQ_EXPORT = 2;
    private static final int MAX_CHIPS = 6;

    private Home home;
    /** null = todos los cuartos. */
    private String room;
    private boolean onSettings;
    private final List<Device> shown = new ArrayList<>();

    private TextView title;
    private ImageView addButton;
    private LinearLayout tabs, settingsBody, navHome, navSettings;
    private HorizontalScrollView tabScroll;
    private GridView grid;
    private LinearLayout empty;
    private TextView emptyTitle, emptyText, emptyButton;
    private ScrollView settingsPage;
    private LinearLayout homePage;
    private final CardAdapter adapter = new CardAdapter();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        home = Home.get(this);
        if (b != null) {
            room = b.getString("room");
            onSettings = b.getBoolean("settings");
        }
        setContentView(build());
        refresh();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("room", room);
        out.putBoolean("settings", onSettings);
    }

    @Override protected void onStart() {
        super.onStart();
        home.addObserver(this);
        home.acquire();
        refresh();
    }

    @Override protected void onStop() {
        super.onStop();
        home.removeObserver(this);
        home.release();
    }

    @Override public void onHomeChanged() { refresh(); }

    // ---- construcción ----

    private View build() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.color(this, R.color.bg));
        Ui.fitSystemBars(root);

        LinearLayout header = Ui.row(this);
        header.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 6));
        title = Ui.oneLine(Ui.text(this, "", 24, R.color.text, true));
        header.addView(title, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        addButton = Ui.roundButton(this, R.drawable.ic_add, 40, "Agregar dispositivo");
        addButton.setBackground(Ui.pressable(this, Ui.color(this, R.color.accent), 20));
        addButton.setImageTintList(ColorStateList.valueOf(Ui.color(this, R.color.on_accent)));
        addButton.setOnClickListener(v -> startActivity(new Intent(this, AddActivity.class)));
        header.addView(addButton, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)));
        root.addView(header);

        FrameLayout pages = new FrameLayout(this);
        root.addView(pages, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        homePage = Ui.column(this);
        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabs = Ui.row(this);
        tabs.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        tabScroll.addView(tabs);
        homePage.addView(tabScroll);

        FrameLayout body = new FrameLayout(this);
        grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(Ui.dp(this, 156));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(Ui.dp(this, 12));
        grid.setVerticalSpacing(Ui.dp(this, 12));
        grid.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 16));
        grid.setClipToPadding(false);
        grid.setSelector(android.R.color.transparent);
        grid.setVerticalScrollBarEnabled(false);
        grid.setAdapter(adapter);
        body.addView(grid);

        empty = Ui.column(this);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(this, 36), 0, Ui.dp(this, 36), Ui.dp(this, 48));
        ImageView art = Ui.icon(this, R.drawable.ic_plug, R.color.text2);
        art.setAlpha(0.5f);
        empty.addView(art, Ui.lp(Ui.dp(this, 72), Ui.dp(this, 72)));
        emptyTitle = Ui.text(this, "", 18, R.color.text, true);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyTitle.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 6));
        empty.addView(emptyTitle);
        emptyText = Ui.text(this, "", 14, R.color.text2, false);
        emptyText.setGravity(Gravity.CENTER);
        empty.addView(emptyText);
        emptyButton = Ui.text(this, "Agregar dispositivo", 15, R.color.on_accent, true);
        emptyButton.setGravity(Gravity.CENTER);
        emptyButton.setBackground(Ui.pressable(this, Ui.color(this, R.color.accent), 24));
        emptyButton.setPadding(Ui.dp(this, 28), 0, Ui.dp(this, 28), 0);
        emptyButton.setOnClickListener(v -> startActivity(new Intent(this, AddActivity.class)));
        LinearLayout.LayoutParams bp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 48));
        bp.topMargin = Ui.dp(this, 20);
        empty.addView(emptyButton, bp);
        body.addView(empty);
        homePage.addView(body, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        pages.addView(homePage);

        settingsPage = new ScrollView(this);
        settingsBody = Ui.column(this);
        settingsBody.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        settingsPage.addView(settingsBody);
        pages.addView(settingsPage);

        LinearLayout nav = Ui.row(this);
        nav.setBackgroundColor(Ui.color(this, R.color.card));
        navHome = navItem(R.drawable.ic_home, "Inicio");
        navSettings = navItem(R.drawable.ic_settings, "Ajustes");
        navHome.setOnClickListener(v -> { onSettings = false; refresh(); });
        navSettings.setOnClickListener(v -> { onSettings = true; refresh(); });
        nav.addView(navHome, Ui.lp(0, Ui.dp(this, 58), 1));
        nav.addView(navSettings, Ui.lp(0, Ui.dp(this, 58), 1));
        root.addView(nav);
        return root;
    }

    private LinearLayout navItem(int icon, String label) {
        LinearLayout l = Ui.column(this);
        l.setGravity(Gravity.CENTER);
        l.addView(Ui.icon(this, icon, R.color.text2), Ui.lp(Ui.dp(this, 24), Ui.dp(this, 24)));
        TextView t = Ui.text(this, label, 11, R.color.text2, false);
        t.setPadding(0, Ui.dp(this, 2), 0, 0);
        l.addView(t);
        l.setContentDescription(label);
        Ui.clickable(l, null);
        return l;
    }

    private void paintNav(LinearLayout item, boolean selected) {
        int c = Ui.color(this, selected ? R.color.accent : R.color.text2);
        ((ImageView) item.getChildAt(0)).setImageTintList(ColorStateList.valueOf(c));
        ((TextView) item.getChildAt(1)).setTextColor(c);
    }

    // ---- refresco ----

    private void refresh() {
        if (title == null) return;
        if (room != null && !home.store.rooms.contains(room)) room = null;
        title.setText(onSettings ? "Ajustes" : home.store.homeName);
        addButton.setVisibility(onSettings ? View.INVISIBLE : View.VISIBLE);
        homePage.setVisibility(onSettings ? View.GONE : View.VISIBLE);
        settingsPage.setVisibility(onSettings ? View.VISIBLE : View.GONE);
        paintNav(navHome, !onSettings);
        paintNav(navSettings, onSettings);
        if (onSettings) { buildSettings(); return; }

        buildTabs();
        shown.clear();
        for (Device d : home.store.devices) {
            if (room == null || room.equals(d.room)) shown.add(d);
        }
        adapter.notifyDataSetChanged();
        boolean none = home.store.devices.isEmpty();
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        grid.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
        tabScroll.setVisibility(none ? View.GONE : View.VISIBLE);
        if (none) {
            emptyTitle.setText("Aún no hay dispositivos");
            emptyText.setText("Agrega tus interruptores y contactos Tuya para controlarlos por tu red local, sin nube.");
            emptyButton.setVisibility(View.VISIBLE);
        } else {
            emptyTitle.setText("Cuarto vacío");
            emptyText.setText("Mantén pulsado un dispositivo y elige «Mover a cuarto».");
            emptyButton.setVisibility(View.GONE);
        }
    }

    private void buildTabs() {
        tabs.removeAllViews();
        addTab("Todos", room == null, v -> { room = null; refresh(); });
        for (String r : home.store.rooms) {
            addTab(r, r.equals(room), v -> { room = r; refresh(); });
        }
        TextView edit = Ui.text(this, "＋ Cuarto", 15, R.color.text2, false);
        edit.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        edit.setOnClickListener(v -> Actions.manageRooms(this, home));
        tabs.addView(edit);
    }

    private void addTab(String label, boolean selected, View.OnClickListener l) {
        TextView t = Ui.text(this, label, selected ? 17 : 15, selected ? R.color.text : R.color.text2, selected);
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        t.setOnClickListener(l);
        tabs.addView(t);
    }

    // ---- ajustes ----

    private void buildSettings() {
        LinearLayout b = settingsBody;
        b.removeAllViews();
        LinearLayout c1 = Ui.card(this);
        addRow(c1, "Nombre del hogar", home.store.homeName, v ->
                Ui.prompt(this, "Nombre del hogar", "Mi hogar", home.store.homeName, s -> {
                    home.store.homeName = s;
                    home.save();
                    refresh();
                }));
        c1.addView(Ui.divider(this));
        int n = home.store.rooms.size();
        addRow(c1, "Cuartos", n == 0 ? "Ninguno" : n + (n == 1 ? " cuarto" : " cuartos"), v -> Actions.manageRooms(this, home));
        b.addView(c1);

        b.addView(Ui.space(this, 1, 16));
        LinearLayout c2 = Ui.card(this);
        addRow(c2, "Agregar o importar dispositivos", "Buscar en la red, archivo de claves o Tuya IoT",
                v -> startActivity(new Intent(this, AddActivity.class)));
        c2.addView(Ui.divider(this));
        addRow(c2, "Exportar respaldo", "Incluye las claves locales: guárdalo en un lugar seguro", v -> export());
        c2.addView(Ui.divider(this));
        addRow(c2, "Reconectar todo", null, v -> {
            home.reconnectAll();
            Actions.toast(this, "Reconectando…");
        });
        b.addView(c2);

        b.addView(Ui.space(this, 1, 16));
        LinearLayout c3 = Ui.card(this);
        addRow(c3, "Cómo funciona y privacidad", null, v -> about());
        b.addView(c3);

        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) { }
        TextView foot = Ui.text(this, "Hogar Local " + version + "\nSin cuenta · sin nube · sin rastreo · sin anuncios", 12, R.color.text2, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Ui.dp(this, 24), 0, 0);
        b.addView(foot);
    }

    private void addRow(LinearLayout card, String titleText, String detail, View.OnClickListener l) {
        LinearLayout r = Ui.listRow(this, titleText, detail, true);
        Ui.clickable(r, l);
        card.addView(r);
    }

    private void about() {
        new AlertDialog.Builder(this)
                .setTitle("Cómo funciona")
                .setMessage("Hogar Local habla directamente con tus dispositivos Tuya por tu red Wi-Fi (puerto 6668). "
                        + "No usa la nube para controlarlos, no pide cuenta y no contiene estadísticas, anuncios ni SDK de terceros.\n\n"
                        + "• Solo funciona cuando el teléfono está en la misma red que los dispositivos.\n"
                        + "• Cada dispositivo necesita su «clave local», que se importa una vez.\n"
                        + "• Los dispositivos siguen vinculados a tu cuenta Tuya; puedes seguir usando Smart Life cuando quieras. "
                        + "Si los vuelves a emparejar allí, la clave local cambia y hay que importarla de nuevo.\n"
                        + "• Las conexiones se abren al ver la app y se cierran al salir: no hay nada en segundo plano.\n\n"
                        + "El único acceso a internet es opcional: «Descargar claves de Tuya IoT», que tú inicias con tus credenciales.")
                .setPositiveButton("Entendido", null)
                .show();
    }

    private void export() {
        if (home.store.devices.isEmpty()) { Actions.toast(this, "No hay dispositivos que exportar"); return; }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "hogar-local-respaldo.json");
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (RuntimeException e) {
            Actions.toast(this, "No hay una app para guardar archivos");
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_EXPORT || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        final byte[] bytes = Json.pretty(home.store.toJson(true)).getBytes(StandardCharsets.UTF_8);
        new Thread(() -> {
            boolean ok = true;
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new java.io.IOException();
                out.write(bytes);
            } catch (Exception e) {
                ok = false;
            }
            final boolean done = ok;
            runOnUiThread(() -> Actions.toast(this, done ? "Respaldo guardado" : "No se pudo guardar el respaldo"));
        }, "export").start();
    }

    // ---- tarjetas ----

    private static final class Holder {
        Device device;
        ImageView icon, power;
        TextView name, sub;
        LinearLayout chips;
    }

    private final class CardAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int pos, View convert, ViewGroup parent) {
            Holder h;
            if (convert == null) {
                h = new Holder();
                convert = newCard(h);
                convert.setTag(h);
            } else {
                h = (Holder) convert.getTag();
            }
            bind(convert, h, shown.get(pos));
            return convert;
        }
    }

    private View newCard(final Holder h) {
        final MainActivity c = this;
        LinearLayout card = Ui.column(c);
        card.setBackground(Ui.pressable(c, Ui.color(c, R.color.card), 18));
        card.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 12));
        card.setLayoutParams(new GridView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 152)));

        LinearLayout top = Ui.row(c);
        h.icon = Ui.icon(c, R.drawable.ic_switch, R.color.text2);
        top.addView(h.icon, Ui.lp(Ui.dp(c, 34), Ui.dp(c, 34)));
        top.addView(new View(c), Ui.lp(0, 1, 1));
        h.power = Ui.roundButton(c, R.drawable.ic_power, 42, "Encender o apagar");
        top.addView(h.power, Ui.lp(Ui.dp(c, 42), Ui.dp(c, 42)));
        card.addView(top);

        card.addView(new View(c), Ui.lp(1, 0, 1));
        h.name = Ui.oneLine(Ui.text(c, "", 15, R.color.text, true));
        card.addView(h.name);
        h.sub = Ui.oneLine(Ui.text(c, "", 12, R.color.text2, false));
        h.sub.setPadding(0, Ui.dp(c, 2), 0, 0);
        card.addView(h.sub);
        h.chips = Ui.row(c);
        LinearLayout.LayoutParams cp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 30));
        cp.topMargin = Ui.dp(c, 8);
        card.addView(h.chips, cp);

        card.setOnClickListener(v -> {
            if (h.device != null) startActivity(new Intent(c, DeviceActivity.class).putExtra("id", h.device.id));
        });
        card.setOnLongClickListener(v -> {
            if (h.device != null) cardMenu(v, h.device);
            return true;
        });
        h.power.setOnClickListener(v -> {
            Device d = h.device;
            if (d == null) return;
            List<Dp> sw = DpLogic.switches(d);
            boolean anyOn = false;
            for (Dp s : sw) anyOn |= d.isOn(s);
            Map<String, Object> m = new LinkedHashMap<>();
            for (Dp s : sw) m.put(s.id, !anyOn);
            send(v, d, m);
        });
        return card;
    }

    private void send(View v, Device d, Map<String, Object> dps) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (!home.control(d, dps)) Actions.toast(this, "«" + d.name + "» no está conectado");
    }

    private void bind(View card, Holder h, final Device d) {
        h.device = d;
        List<Dp> sw = DpLogic.switches(d);
        int kids = d.isSub() ? 0 : home.store.childrenOf(d.id).size();
        boolean hub = sw.isEmpty() && kids > 0;
        boolean online = d.state == Device.ONLINE;
        int on = 0;
        for (Dp s : sw) if (d.isOn(s)) on++;

        h.name.setText(d.name);
        h.icon.setImageResource(Ui.iconFor(d, sw.size(), hub));
        h.icon.setImageTintList(ColorStateList.valueOf(Ui.color(this, online && on > 0 ? R.color.accent : R.color.text2)));

        String status;
        if (!online) status = d.state == Device.CONNECTING ? "Conectando…" : Ui.errorText(d);
        else if (hub) status = kids + (kids == 1 ? " dispositivo" : " dispositivos");
        else if (sw.isEmpty()) status = "En línea";
        else if (sw.size() == 1) status = on == 1 ? "Encendido" : "Apagado";
        else status = on == 0 ? "Apagado" : on + " de " + sw.size() + " encendidos";
        if (room == null && !d.room.isEmpty()) status = d.room + " · " + status;
        h.sub.setText(status);

        h.power.setVisibility(sw.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        Ui.paintPower(h.power, on > 0, online, 42);
        h.name.setAlpha(online ? 1f : 0.6f);

        int chips = sw.size() > 1 ? Math.min(sw.size(), MAX_CHIPS) : 0;
        h.chips.setVisibility(chips == 0 ? View.GONE : View.VISIBLE);
        if (h.chips.getChildCount() != chips) {
            h.chips.removeAllViews();
            for (int i = 0; i < chips; i++) {
                TextView t = Ui.text(this, String.valueOf(i + 1), 13, R.color.text2, true);
                t.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams p = Ui.lp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                if (i > 0) p.leftMargin = Ui.dp(this, 6);
                h.chips.addView(t, p);
            }
        }
        for (int i = 0; i < chips; i++) {
            final Dp s = sw.get(i);
            final boolean isOn = d.isOn(s);
            TextView t = (TextView) h.chips.getChildAt(i);
            t.setBackground(Ui.pressable(this, Ui.color(this, isOn ? R.color.accent : R.color.chip), 15));
            t.setTextColor(Ui.color(this, isOn ? R.color.on_accent : R.color.text2));
            t.setAlpha(online ? 1f : 0.45f);
            t.setContentDescription(DpLogic.label(s, i, sw.size()) + (isOn ? ", encendido" : ", apagado"));
            t.setOnClickListener(v -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put(s.id, !d.isOn(s));
                send(v, d, m);
            });
        }
    }

    private void cardMenu(View anchor, final Device d) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Cambiar nombre");
        m.getMenu().add(0, 2, 1, "Mover a cuarto");
        m.getMenu().add(0, 3, 2, "Eliminar");
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) Actions.rename(this, home, d);
            else if (item.getItemId() == 2) Actions.pickRoom(this, home, d);
            else Actions.confirmDelete(this, home, d, null);
            return true;
        });
        m.show();
    }
}
