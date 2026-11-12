package app.hogarlocal.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.hogarlocal.Home;
import app.hogarlocal.R;
import app.hogarlocal.model.Device;
import app.hogarlocal.model.DpLogic;
import app.hogarlocal.model.Importer;
import app.hogarlocal.proto.CloudClient;
import app.hogarlocal.proto.Discovery;
import app.hogarlocal.proto.Json;

/** Alta de dispositivos: lo que se anuncia en la red, archivo de claves, Tuya IoT o a mano. */
public final class AddActivity extends Activity implements Home.Observer {
    private static final int REQ_IMPORT = 1;
    private static final int MAX_IMPORT_BYTES = 4 * 1024 * 1024;

    private Home home;
    private LinearLayout foundCard;
    private TextView foundHint;
    private String foundSignature = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        home = Home.get(this);
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
        TextView title = Ui.text(this, "Agregar dispositivos", 19, R.color.text, true);
        title.setPadding(Ui.dp(this, 4), 0, 0, 0);
        bar.addView(title);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = Ui.column(this);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(body);
        root.addView(scroll, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        body.addView(section("Claves locales"));
        LinearLayout c = Ui.card(this);
        LinearLayout r1 = Ui.listRow(this, "Importar archivo de claves", "devices.json de tinytuya o un respaldo de esta app", true);
        Ui.clickable(r1, v -> pickFile());
        c.addView(r1);
        c.addView(Ui.divider(this));
        LinearLayout r2 = Ui.listRow(this, "Descargar de Tuya IoT", "Una sola vez, con tu cuenta de desarrollador", true);
        Ui.clickable(r2, v -> cloudDialog());
        c.addView(r2);
        c.addView(Ui.divider(this));
        LinearLayout r3 = Ui.listRow(this, "Agregar a mano", "Con identificador y clave local", true);
        Ui.clickable(r3, v -> Actions.connection(this, home, null, null, "", null));
        c.addView(r3);
        body.addView(c);

        body.addView(section("En tu red"));
        foundHint = Ui.text(this, "", 13, R.color.text2, false);
        foundHint.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), Ui.dp(this, 10));
        body.addView(foundHint);
        foundCard = Ui.card(this);
        body.addView(foundCard);
        setContentView(root);
    }

    private TextView section(String s) {
        TextView t = Ui.text(this, s, 13, R.color.text2, true);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 14), 0, Ui.dp(this, 8));
        return t;
    }

    @Override protected void onStart() {
        super.onStart();
        home.addObserver(this);
        home.acquire();
        foundSignature = "";
        refresh();
        // El puerto de escucha tarda un instante en abrirse.
        foundCard.postDelayed(this::refresh, 800);
    }

    @Override protected void onStop() {
        super.onStop();
        home.removeObserver(this);
        home.release();
    }

    @Override public void onHomeChanged() { refresh(); }

    private void refresh() {
        StringBuilder sig = new StringBuilder(home.discoveryBlocked() ? "x" : "");
        for (Discovery.Found f : home.found.values()) {
            sig.append(f.id).append(f.ip).append(home.store.find(f.id) != null).append(';');
        }
        if (sig.toString().equals(foundSignature) && foundCard.getChildCount() > 0) return;
        foundSignature = sig.toString();
        foundCard.removeAllViews();
        if (home.discoveryBlocked()) {
            foundHint.setText("No se pudo escuchar la red: otra app (por ejemplo Smart Life) tiene ocupado el puerto. Ciérrala y vuelve a entrar.");
        } else {
            foundHint.setText("Los dispositivos Tuya se anuncian solos cada pocos segundos. Aquí aparecen los que están en tu misma red Wi-Fi.");
        }
        if (home.found.isEmpty()) {
            foundCard.addView(Ui.listRow(this, "Buscando…", "Asegúrate de estar en la misma red Wi-Fi (2.4 GHz) que tus dispositivos", false));
            return;
        }
        boolean first = true;
        for (final Discovery.Found f : home.found.values()) {
            if (!first) foundCard.addView(Ui.divider(this));
            first = false;
            final Device known = home.store.find(f.id);
            String name = known != null ? known.name : "Dispositivo " + shortId(f.id);
            String detail = f.ip + " · protocolo " + (f.version == null ? "?" : f.version)
                    + (known != null ? " · ya agregado" : " · falta su clave local");
            LinearLayout row = Ui.listRow(this, name, detail, known == null);
            if (known == null) Ui.clickable(row, v -> Actions.connection(this, home, null, f.id, f.ip, f.version));
            foundCard.addView(row);
        }
    }

    private static String shortId(String id) {
        return id.length() <= 6 ? id : "…" + id.substring(id.length() - 6);
    }

    // ---- archivo ----

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (RuntimeException e) {
            Actions.toast(this, "No hay una app para elegir archivos");
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMPORT || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        new Thread(() -> {
            Importer.Result result = null;
            String error = null;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new java.io.IOException("no se pudo abrir");
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bo.write(buf, 0, n);
                    if (bo.size() > MAX_IMPORT_BYTES) throw new java.io.IOException("archivo demasiado grande");
                }
                result = Importer.parse(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                error = "El archivo no es una lista de dispositivos válida";
            } catch (Exception e) {
                error = "No se pudo leer el archivo";
            }
            final Importer.Result r = result;
            final String err = error;
            runOnUiThread(() -> {
                if (r == null) { message("Importar", err); return; }
                applyImport(r, "archivo");
            });
        }, "import").start();
    }

    private void applyImport(Importer.Result r, String source) {
        if (r.devices.isEmpty()) { message("Importar", "No se encontraron dispositivos en " + source + "."); return; }
        int noKey = 0;
        for (Device d : r.devices) if (!d.isSub() && !Home.validKey(d.key)) noKey++;
        if (r.homeName != null && home.store.devices.isEmpty()) home.store.homeName = r.homeName;
        int[] n = Importer.merge(home.store, r);
        home.save();
        home.sync();
        foundSignature = "";
        refresh();
        String msg = n[0] + " nuevo(s), " + n[1] + " actualizado(s).";
        if (noKey > 0) msg += "\n\n" + noKey + " sin clave local válida: no se podrán controlar hasta que la tengan.";
        msg += "\n\nLas direcciones IP se detectan solas cuando cada dispositivo se anuncia en la red.";
        message("Importación lista", msg);
    }

    private void message(String title, String text) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("Aceptar", null).show();
    }

    // ---- Tuya IoT ----

    private void cloudDialog() {
        LinearLayout col = Ui.column(this);
        TextView info = Ui.text(this, "Con un proyecto «Cloud» gratuito en iot.tuya.com enlazado a tu cuenta de Smart Life. "
                + "La app se conecta una vez a la API de Tuya, baja nombres y claves locales, y no guarda estas credenciales.",
                13, R.color.text2, false);
        info.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
        col.addView(info);
        col.addView(Actions.label(this, "Centro de datos"));
        final Spinner region = new Spinner(this);
        String[] names = new String[CloudClient.REGIONS.length];
        for (int i = 0; i < names.length; i++) names[i] = CloudClient.REGIONS[i][0];
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        region.setAdapter(ad);
        col.addView(region);
        col.addView(Actions.label(this, "Access ID / Client ID"));
        final EditText id = Ui.field(this, "Access ID", "");
        col.addView(id);
        col.addView(Actions.label(this, "Access Secret / Client Secret"));
        final EditText secret = Ui.field(this, "Access Secret", "");
        secret.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        col.addView(secret);
        new AlertDialog.Builder(this)
                .setTitle("Descargar de Tuya IoT")
                .setView(Ui.padded(this, col))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Descargar", (d, w) -> {
                    String a = id.getText().toString().trim(), s = secret.getText().toString().trim();
                    if (a.isEmpty() || s.isEmpty()) { Actions.toast(this, "Faltan las credenciales"); return; }
                    download(CloudClient.REGIONS[region.getSelectedItemPosition()][1], a, s);
                })
                .show();
    }

    private void download(final String base, final String id, final String secret) {
        final AlertDialog progress = new AlertDialog.Builder(this)
                .setMessage("Descargando la lista de dispositivos…")
                .setCancelable(false)
                .create();
        progress.show();
        new Thread(() -> {
            String error = null;
            final Importer.Result r = new Importer.Result();
            try {
                CloudClient c = new CloudClient(base, id, secret);
                c.login();
                List<Map<String, Object>> list = c.devices();
                r.devices.addAll(Importer.fromCloud(list));
                // Un mismo modelo comparte funciones: basta pedirlas una vez por producto.
                Map<String, String> productOf = new HashMap<>(), sourceOf = new HashMap<>();
                for (Map<String, Object> m : list) productOf.put(Json.str(m, "id", ""), Json.str(m, "product_id", ""));
                for (Device d : r.devices) {
                    String product = productOf.get(d.id);
                    boolean hasProduct = product != null && !product.isEmpty();
                    String source = hasProduct ? sourceOf.get(product) : null;
                    if (source != null) {
                        d.dps.addAll(copyOf(r, source));
                        continue;
                    }
                    Map<String, Object> specs = c.specifications(d.id);
                    if (specs == null) continue;
                    DpLogic.applySpecs(d, specs);
                    if (hasProduct) sourceOf.put(product, d.id);
                }
            } catch (CloudClient.CloudException e) {
                error = "Tuya respondió: " + e.getMessage()
                        + "\n\nRevisa el centro de datos, las credenciales y que el proyecto tenga enlazada tu cuenta de la app.";
            } catch (Exception e) {
                error = "No se pudo conectar con Tuya. Revisa tu conexión a internet.";
            }
            final String err = error;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) progress.dismiss();
                if (err != null) message("Tuya IoT", err);
                else applyImport(r, "tu cuenta de Tuya");
            });
        }, "cloud").start();
    }

    /** Copia los DP (códigos y tipos) de un dispositivo del mismo producto. */
    private static List<app.hogarlocal.model.Dp> copyOf(Importer.Result r, String sourceId) {
        List<app.hogarlocal.model.Dp> out = new ArrayList<>();
        for (Device d : r.devices) {
            if (!d.id.equals(sourceId)) continue;
            for (app.hogarlocal.model.Dp p : d.dps) {
                app.hogarlocal.model.Dp q = new app.hogarlocal.model.Dp();
                q.id = p.id;
                q.code = p.code;
                q.type = p.type;
                out.add(q);
            }
        }
        return out;
    }
}
