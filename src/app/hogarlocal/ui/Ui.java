package app.hogarlocal.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.hogarlocal.R;
import app.hogarlocal.model.Device;
import app.hogarlocal.model.DpLogic;

/** Piezas de interfaz compartidas; todo se construye en código, sin bibliotecas. */
final class Ui {
    private Ui() { }

    interface Text {
        void accept(String value);
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static int color(Context c, int id) { return c.getColor(id); }

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    /** Fondo redondeado con onda al tocar. */
    static Drawable pressable(Context c, int color, float radiusDp) {
        float r = dp(c, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(0x22808080), round(color, r), round(0xFFFFFFFF, r));
    }

    static TextView text(Context c, String s, float sp, int colorId, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color(c, colorId));
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    static TextView oneLine(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static ImageView icon(Context c, int drawable, int colorId) {
        ImageView v = new ImageView(c);
        v.setImageResource(drawable);
        v.setImageTintList(ColorStateList.valueOf(color(c, colorId)));
        return v;
    }

    /** Botón redondo con icono; el tamaño incluye el área táctil. */
    static ImageView roundButton(Context c, int drawable, int sizeDp, String description) {
        ImageView v = icon(c, drawable, R.color.text);
        int pad = dp(c, sizeDp * 0.24f);
        v.setPadding(pad, pad, pad, pad);
        v.setBackground(pressable(c, color(c, R.color.chip), sizeDp / 2f));
        v.setContentDescription(description);
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    /** Pinta un botón de encendido según su estado. */
    static void paintPower(ImageView v, boolean on, boolean enabled, float sizeDp) {
        Context c = v.getContext();
        v.setBackground(pressable(c, color(c, on ? R.color.accent : R.color.chip), sizeDp / 2f));
        v.setImageTintList(ColorStateList.valueOf(color(c, on ? R.color.on_accent : R.color.text2)));
        v.setAlpha(enabled ? 1f : 0.45f);
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static View space(Context c, int wDp, int hDp) {
        View v = new View(c);
        v.setLayoutParams(lp(dp(c, wDp), dp(c, hDp)));
        return v;
    }

    /** Fila de lista: título, detalle opcional y flecha. */
    static LinearLayout listRow(Context c, String title, String detail, boolean arrow) {
        LinearLayout r = row(c);
        r.setPadding(dp(c, 16), dp(c, 14), dp(c, 12), dp(c, 14));
        r.setMinimumHeight(dp(c, 56));
        LinearLayout col = column(c);
        col.addView(text(c, title, 16, R.color.text, false));
        if (detail != null && !detail.isEmpty()) {
            TextView d = text(c, detail, 13, R.color.text2, false);
            d.setPadding(0, dp(c, 2), 0, 0);
            col.addView(d);
        }
        r.addView(col, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (arrow) r.addView(icon(c, R.drawable.ic_next, R.color.text2), lp(dp(c, 20), dp(c, 20)));
        return r;
    }

    /** Tarjeta blanca que agrupa filas. */
    static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setBackground(round(color(c, R.color.card), dp(c, 16)));
        l.setClipToOutline(true);
        return l;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(color(c, R.color.line));
        LinearLayout.LayoutParams p = lp(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.5f)));
        p.leftMargin = dp(c, 16);
        v.setLayoutParams(p);
        return v;
    }

    static void clickable(View v, View.OnClickListener l) {
        Context c = v.getContext();
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        v.setForeground(c.getDrawable(tv.resourceId));
        v.setOnClickListener(l);
    }

    /** Con el diseño de borde a borde, deja libres la barra de estado y la de navegación. */
    static void fitSystemBars(View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
    }

    static EditText field(Context c, String hint, String value) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (value != null) {
            e.setText(value);
            e.setSelection(e.getText().length());
        }
        return e;
    }

    static FrameLayout padded(Context c, View content) {
        FrameLayout f = new FrameLayout(c);
        int p = dp(c, 20);
        f.setPadding(p, dp(c, 8), p, 0);
        f.addView(content);
        return f;
    }

    /** Diálogo para pedir un texto corto. */
    static void prompt(Activity a, String title, String hint, String value, Text onOk) {
        EditText e = field(a, hint, value);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(title)
                .setView(padded(a, e))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (x, w) -> {
                    String s = e.getText().toString().trim();
                    if (!s.isEmpty()) onOk.accept(s);
                })
                .create();
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        e.requestFocus();
    }

    // ---- textos del dominio ----

    static int iconFor(Device d, int switches, boolean isHub) {
        if (isHub) return R.drawable.ic_hub;
        String cat = d.category;
        if (cat.equals("cz") || cat.equals("pc")) return R.drawable.ic_plug;
        if (cat.equals("kg") || cat.equals("tdq") || switches > 1) return R.drawable.ic_switch;
        return DpLogic.watts(d) != null || d.dp("9") != null ? R.drawable.ic_plug : R.drawable.ic_switch;
    }

    static String errorText(Device d) {
        String e = d.error;
        if (e == null) return "Sin conexión";
        switch (e) {
            case "sin_clave": return "Falta la clave local";
            case "sin_ip": return "Buscando en la red…";
            case "sin_hub": return "Falta su hub";
            case "hub_fuera": return "Hub sin conexión";
            case "hijo_mudo": return "No responde por el hub";
            case "clave": return "Clave local incorrecta";
            case "sin_respuesta": return "No responde (revisa clave)";
            case "inalcanzable": return "Sin conexión";
            default: return "Sin conexión";
        }
    }

    static String duration(long seconds) {
        if (seconds >= 3600) {
            long h = seconds / 3600, m = (seconds % 3600) / 60;
            return m == 0 ? h + " h" : h + " h " + m + " min";
        }
        if (seconds >= 60) return (seconds + 30) / 60 + " min";
        return seconds + " s";
    }
}
