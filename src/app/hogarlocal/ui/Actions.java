package app.hogarlocal.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import app.hogarlocal.Home;
import app.hogarlocal.R;
import app.hogarlocal.model.Device;
import app.hogarlocal.model.Importer;
import app.hogarlocal.proto.TuyaSession;

/** Diálogos de edición que comparten varias pantallas. */
final class Actions {
    static final String[] VERSIONS = {"auto", "3.1", "3.3", "3.4", "3.5"};

    private Actions() { }

    static void toast(Activity a, String s) {
        Toast.makeText(a, s, Toast.LENGTH_SHORT).show();
    }

    static void rename(Activity a, Home home, Device d) {
        Ui.prompt(a, "Nombre del dispositivo", "Nombre", d.name, s -> {
            d.name = s;
            home.save();
            home.changed();
        });
    }

    static void pickRoom(Activity a, Home home, Device d) {
        List<String> names = new ArrayList<>();
        names.add("Sin cuarto");
        names.addAll(home.store.rooms);
        names.add("Nuevo cuarto…");
        int checked = Math.max(0, names.indexOf(d.room));
        new AlertDialog.Builder(a)
                .setTitle("Cuarto")
                .setSingleChoiceItems(names.toArray(new String[0]), checked, (dlg, which) -> {
                    dlg.dismiss();
                    if (which == names.size() - 1) {
                        newRoom(a, home, room -> {
                            d.room = room;
                            home.save();
                            home.changed();
                        });
                        return;
                    }
                    d.room = which == 0 ? "" : names.get(which);
                    home.save();
                    home.changed();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    static void newRoom(Activity a, Home home, Ui.Text then) {
        Ui.prompt(a, "Nuevo cuarto", "Sala, Cocina, Recámara…", "", s -> {
            if (!home.store.rooms.contains(s)) home.store.rooms.add(s);
            home.save();
            home.changed();
            if (then != null) then.accept(s);
        });
    }

    static void manageRooms(Activity a, Home home) {
        List<String> items = new ArrayList<>(home.store.rooms);
        items.add("＋ Nuevo cuarto");
        new AlertDialog.Builder(a)
                .setTitle("Cuartos")
                .setItems(items.toArray(new String[0]), (dlg, which) -> {
                    if (which == items.size() - 1) {
                        newRoom(a, home, s -> manageRooms(a, home));
                        return;
                    }
                    String room = items.get(which);
                    new AlertDialog.Builder(a)
                            .setTitle(room)
                            .setItems(new String[]{"Cambiar nombre", "Eliminar cuarto"}, (d2, w2) -> {
                                if (w2 == 0) {
                                    Ui.prompt(a, "Nombre del cuarto", "Nombre", room, s -> {
                                        int i = home.store.rooms.indexOf(room);
                                        if (i < 0 || home.store.rooms.contains(s)) return;
                                        home.store.rooms.set(i, s);
                                        for (Device d : home.store.devices) if (d.room.equals(room)) d.room = s;
                                        home.save();
                                        home.changed();
                                    });
                                } else {
                                    home.store.rooms.remove(room);
                                    for (Device d : home.store.devices) if (d.room.equals(room)) d.room = "";
                                    home.save();
                                    home.changed();
                                    toast(a, "Cuarto eliminado; sus dispositivos se conservan");
                                }
                            })
                            .show();
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    static void confirmDelete(Activity a, Home home, Device d, Runnable after) {
        int kids = home.store.childrenOf(d.id).size();
        String extra = kids > 0 ? "\n\nEs el hub de " + kids + " dispositivo(s): quedarán sin conexión." : "";
        new AlertDialog.Builder(a)
                .setTitle("Eliminar «" + d.name + "»")
                .setMessage("Se quita de esta app junto con su clave local. El dispositivo no se desvincula de tu cuenta Tuya." + extra)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dlg, w) -> {
                    home.remove(d);
                    if (after != null) after.run();
                })
                .show();
    }

    static Spinner versionSpinner(Activity a, String current) {
        Spinner sp = new Spinner(a);
        String[] labels = {"Automática", "3.1", "3.3", "3.4", "3.5"};
        ArrayAdapter<String> ad = new ArrayAdapter<>(a, android.R.layout.simple_spinner_item, labels);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        for (int i = 0; i < VERSIONS.length; i++) if (VERSIONS[i].equals(current)) sp.setSelection(i);
        return sp;
    }

    static TextView label(Activity a, String s) {
        TextView t = Ui.text(a, s, 12, R.color.text2, false);
        t.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), 0, 0);
        return t;
    }

    /**
     * Formulario de conexión. Con device == null crea uno nuevo (pide también nombre e id).
     * @param presetId id ya conocido (por ejemplo, de un anuncio en la red) o null
     */
    static void connection(Activity a, Home home, Device device, String presetId, String presetIp, String presetVersion) {
        boolean isNew = device == null;
        LinearLayout col = Ui.column(a);
        EditText name = Ui.field(a, "Nombre (p. ej. Luz de la sala)", isNew ? "" : device.name);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText id = Ui.field(a, "Identificador del dispositivo", isNew ? presetId : device.id);
        EditText key = Ui.field(a, "Clave local (16 caracteres)", isNew ? "" : device.key);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        EditText ip = Ui.field(a, "IP local (vacío = buscar sola)", isNew ? presetIp : device.ip);
        ip.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        Spinner ver = versionSpinner(a, isNew ? (presetVersion == null ? "auto" : presetVersion) : device.version);
        boolean sub = !isNew && device.isSub();
        if (isNew) {
            col.addView(label(a, "Nombre"));
            col.addView(name);
            if (presetId == null) {
                col.addView(label(a, "Identificador"));
                col.addView(id);
            }
        }
        if (sub) {
            TextView t = Ui.text(a, "Se controla por su hub; usa la clave y la conexión del hub.", 14, R.color.text2, false);
            t.setPadding(Ui.dp(a, 4), Ui.dp(a, 8), 0, 0);
            col.addView(t);
        } else {
            col.addView(label(a, "Clave local"));
            col.addView(key);
            col.addView(label(a, "Dirección IP"));
            col.addView(ip);
            col.addView(label(a, "Versión del protocolo"));
            col.addView(ver, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        AlertDialog dlg = new AlertDialog.Builder(a)
                .setTitle(isNew ? "Agregar dispositivo" : "Conexión")
                .setView(Ui.padded(a, col))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null)
                .create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (sub) { dlg.dismiss(); return; }
            String k = key.getText().toString().trim();
            String addr = ip.getText().toString().trim();
            String devId = id.getText() == null ? "" : id.getText().toString().trim();
            if (!Home.validKey(k)) { key.setError("La clave local tiene 16 caracteres"); return; }
            if (!addr.isEmpty() && !Importer.isLocalIp(addr)) { ip.setError("Debe ser una IP de tu red local"); return; }
            Device d = device;
            if (isNew) {
                if (devId.isEmpty()) { id.setError("Falta el identificador"); return; }
                if (home.store.find(devId) != null) { id.setError("Ese dispositivo ya está agregado"); return; }
                d = new Device();
                d.id = devId;
                String n = name.getText().toString().trim();
                d.name = n.isEmpty() ? "Dispositivo" : n;
                home.store.devices.add(d);
            }
            d.key = k;
            d.ip = addr;
            String chosen = VERSIONS[ver.getSelectedItemPosition()];
            d.version = TuyaSession.isVersion(chosen) ? chosen : "auto";
            home.save();
            home.sync();
            dlg.dismiss();
            if (isNew) toast(a, "Dispositivo agregado");
        }));
        dlg.show();
    }
}
