package app.hogarlocal.proto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cliente mínimo de la API de Tuya IoT (openapi). Se usa una sola vez, a petición
 * del usuario y con sus propias credenciales de desarrollador, para bajar las
 * «local key» y los nombres de sus dispositivos. Nada más habla con la nube.
 */
public final class CloudClient {
    public static final String[][] REGIONS = {
            {"América (oeste) — México, EE. UU.", "https://openapi.tuyaus.com"},
            {"América (este)", "https://openapi-ueaz.tuyaus.com"},
            {"Europa central", "https://openapi.tuyaeu.com"},
            {"Europa occidental", "https://openapi-weaz.tuyaeu.com"},
            {"India", "https://openapi.tuyain.com"},
            {"China", "https://openapi.tuyacn.com"},
            {"Singapur", "https://openapi-sg.iotbing.com"},
    };

    public static final class CloudException extends IOException {
        public CloudException(String m) { super(m); }
    }

    private final String base, clientId, secret;
    private String token;

    public CloudClient(String base, String clientId, String secret) {
        this.base = base;
        this.clientId = clientId.trim();
        this.secret = secret.trim();
    }

    /** Firma según «new signature algorithm» de Tuya: HMAC-SHA256 en mayúsculas. */
    public static String sign(String clientId, String secret, String token, String t, String method, String pathAndQuery) {
        String emptyBodyHash = TuyaCodec.hex(TuyaCodec.sha256(new byte[0]));
        String msg = clientId + (token == null ? "" : token) + t + method + "\n" + emptyBodyHash + "\n\n" + pathAndQuery;
        return TuyaCodec.hex(TuyaCodec.hmac(secret.getBytes(StandardCharsets.UTF_8),
                msg.getBytes(StandardCharsets.UTF_8))).toUpperCase(Locale.ROOT);
    }

    /** @param pathAndQuery con los parámetros en orden alfabético, como exige la firma */
    private Map<String, Object> get(String pathAndQuery) throws IOException {
        String t = String.valueOf(System.currentTimeMillis());
        HttpURLConnection c = (HttpURLConnection) new URL(base + pathAndQuery).openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("client_id", clientId);
            c.setRequestProperty("sign", sign(clientId, secret, token, t, "GET", pathAndQuery));
            c.setRequestProperty("t", t);
            c.setRequestProperty("sign_method", "HMAC-SHA256");
            if (token != null) c.setRequestProperty("access_token", token);
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) throw new CloudException("HTTP " + code);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > 8 * 1024 * 1024) throw new CloudException("respuesta demasiado grande");
            }
            in.close();
            Map<String, Object> m;
            try {
                m = Json.parseObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                throw new CloudException("HTTP " + code + ": respuesta no válida");
            }
            if (!Json.bool(m, "success", false)) {
                throw new CloudException(Json.str(m, "msg", "error") + " (código " + Json.str(m, "code", "?") + ")");
            }
            return m;
        } finally {
            c.disconnect();
        }
    }

    public void login() throws IOException {
        token = null;
        Map<String, Object> r = Json.obj(get("/v1.0/token?grant_type=1"), "result");
        token = Json.str(r, "access_token", null);
        if (token == null) throw new CloudException("la nube no devolvió un token");
    }

    /** Todos los dispositivos de las cuentas enlazadas al proyecto, con su local_key. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> devices() throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        String last = null;
        for (int page = 0; page < 40; page++) {
            String q = "/v1.0/iot-01/associated-users/devices?"
                    + (last != null ? "last_row_key=" + last + "&" : "") + "size=50";
            Map<String, Object> r = Json.obj(get(q), "result");
            List<Object> l = Json.arr(r, "devices");
            if (l == null) l = Json.arr(r, "list");
            if (l != null) {
                for (Object o : l) if (o instanceof Map) out.add((Map<String, Object>) o);
            }
            last = Json.str(r, "last_row_key", null);
            if (!Json.bool(r, "has_more", false) || last == null || last.isEmpty()) break;
        }
        return out;
    }

    /** Funciones y estados (código, dp_id, tipo) de un dispositivo; null si no tiene. */
    public Map<String, Object> specifications(String deviceId) {
        try {
            return Json.obj(get("/v1.0/devices/" + deviceId + "/specifications"), "result");
        } catch (IOException e) {
            return null;
        }
    }
}
