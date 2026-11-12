import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import com.sun.net.httpserver.HttpServer;

import app.hogarlocal.model.Device;
import app.hogarlocal.model.Dp;
import app.hogarlocal.model.DpLogic;
import app.hogarlocal.model.Importer;
import app.hogarlocal.model.Store;
import app.hogarlocal.proto.CloudClient;
import app.hogarlocal.proto.Discovery;
import app.hogarlocal.proto.Json;
import app.hogarlocal.proto.TuyaCodec;
import app.hogarlocal.proto.TuyaSession;

/** Pruebas en la JVM: sin Android y sin dispositivos reales. */
public final class Tests {
    static int passed, failed;
    static final String ID = "eb0123456789abcdefghij", KEY = "0123456789abcdef";
    static final byte[] KB = KEY.getBytes(StandardCharsets.US_ASCII);
    static final Map<String, byte[]> V = new HashMap<>();
    static String signVector;

    static void check(String name, boolean ok) {
        if (ok) passed++;
        else { failed++; System.out.println("  FALLA: " + name); }
    }

    static void eq(String name, Object a, Object b) {
        boolean ok = a == null ? b == null : a.equals(b);
        if (!ok) System.out.println("    esperado <" + b + "> obtenido <" + a + ">");
        check(name, ok);
    }

    static boolean waitFor(BooleanSupplier c, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (c.getAsBoolean()) return true;
            Thread.sleep(15);
        }
        return c.getAsBoolean();
    }

    static byte[] unhex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    /** Registra lo que la sesión comunica. */
    static final class Rec implements TuyaSession.Listener {
        final List<Integer> states = new CopyOnWriteArrayList<>();
        final List<String> errors = new CopyOnWriteArrayList<>();
        final Map<String, Object> dps = new ConcurrentHashMap<>();
        final Map<String, Map<String, Object>> byCid = new ConcurrentHashMap<>();
        volatile int state = -1;

        @Override public void onState(TuyaSession s, int st, String error) {
            state = st;
            states.add(st);
            if (error != null) errors.add(error);
        }

        @Override public void onDps(TuyaSession s, String cid, Map<String, Object> d) {
            Map<String, Object> target = cid == null ? dps : byCid.computeIfAbsent(cid, k -> new ConcurrentHashMap<>());
            for (Map.Entry<String, Object> e : d.entrySet()) if (e.getValue() != null) target.put(e.getKey(), e.getValue());
        }
    }

    static final List<TuyaSession> live = new CopyOnWriteArrayList<>();

    static TuyaSession open(FakeDevice f, String key, String version, Rec r) {
        TuyaSession s = new TuyaSession(f.id, "127.0.0.1", f.port(), key, version, r);
        live.add(s);
        s.start();
        return s;
    }

    static void done(TuyaSession s, FakeDevice f) {
        live.remove(s);
        s.close();
        f.close();
    }

    static FakeDevice fake(String version) throws Exception {
        FakeDevice f = new FakeDevice(ID, KEY, version, 0);
        f.dps.put("1", false);
        f.dps.put("2", true);
        f.dps.put("9", 0L);
        return f;
    }

    public static void main(String[] a) throws Exception {
        for (String line : Files.readAllLines(new File("test/vectors.txt").toPath())) {
            int i = line.indexOf('=');
            if (line.startsWith("sign=")) signVector = line.substring(5);
            else V.put(line.substring(0, i), unhex(line.substring(i + 1)));
        }
        Thread ticker = new Thread(() -> {
            while (true) {
                long now = System.currentTimeMillis();
                for (TuyaSession s : live) s.tick(now);
                try { Thread.sleep(200); } catch (InterruptedException e) { return; }
            }
        });
        ticker.setDaemon(true);
        ticker.start();

        json();
        codec();
        clientFrames();
        for (String v : new String[]{"3.1", "3.3", "3.4", "3.5"}) session(v);
        for (String v : new String[]{"3.1", "3.3", "3.4", "3.5"}) autoVersion(v);
        wrongKey();
        reconnect();
        device22();
        hub("3.3");
        hub("3.4");
        heartbeat();
        unreachable();
        discovery();
        cloud();
        model();

        System.out.println(passed + " pruebas correctas, " + failed + " fallidas");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void json() {
        System.out.println("JSON");
        Map<String, Object> m = Json.parseObject(" {\"a\": 1, \"b\": [true, null, \"x\\n\\u00e1\"], \"c\": {\"d\": -2.5}} ");
        eq("entero", m.get("a"), 1L);
        eq("lista", ((List<?>) m.get("b")).size(), 3);
        eq("escape", ((List<?>) m.get("b")).get(2), "x\ná");
        eq("real", Json.obj(m, "c").get("d"), -2.5);
        Map<String, Object> o = new LinkedHashMap<>();
        Map<String, Object> dps = new LinkedHashMap<>();
        dps.put("1", true);
        dps.put("9", 60L);
        dps.put("3", null);
        o.put("t", 5);
        o.put("dps", dps);
        eq("compacto", Json.write(o), "{\"t\":5,\"dps\":{\"1\":true,\"9\":60,\"3\":null}}");
        eq("comillas", Json.write("a\"b\\"), "\"a\\\"b\\\\\"");
        eq("ida y vuelta", Json.write(Json.parse(Json.pretty(o))), Json.write(o));
        for (String bad : new String[]{"", "{", "{\"a\"}", "[1,]", "{\"a\":1}x", "nul", "\"abc"}) {
            boolean threw = false;
            try { Json.parse(bad); } catch (IllegalArgumentException e) { threw = true; }
            check("rechaza <" + bad + ">", threw);
        }
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 5000; i++) deep.append('[');
        boolean threw = false;
        try { Json.parse(deep.toString()); } catch (IllegalArgumentException e) { threw = true; }
        check("límite de profundidad", threw);
    }

    static void codec() throws Exception {
        System.out.println("Tramas (contra vectores independientes)");
        check("55AA con CRC", Arrays.equals(TuyaCodec.pack55(7, 10, "{\"gwId\":\"abc\"}".getBytes(), null, null), V.get("crc_plain")));
        check("55AA con HMAC", Arrays.equals(TuyaCodec.pack55(3, 13, TuyaCodec.ecbEncrypt(KB, "hola".getBytes(), true), KB, null), V.get("hmac_frame")));
        check("6699 GCM", Arrays.equals(TuyaCodec.pack66(5, 16, "{}".getBytes(), KB, null, "ABCDEFGHIJKL".getBytes()), V.get("gcm_frame")));

        TuyaCodec.Frame f = TuyaCodec.decode(V.get("dev_crc_ret"), null, false, true);
        check("CRC válido", f.valid);
        eq("comando", f.cmd, 10);
        eq("secuencia", f.seq, 9);
        eq("3.3 con código de retorno", new String(TuyaCodec.ecbDecrypt(KB, f.payload)), "{\"dps\":{\"1\":true}}");

        f = TuyaCodec.decode(V.get("gcm_dev"), KB, false, true);
        check("GCM válido", f.valid);
        check("cabecera 3.5", TuyaCodec.startsWith(f.payload, "3.5"));
        eq("contenido 3.5", new String(f.payload, 15, f.payload.length - 15), "{\"data\":{\"dps\":{\"2\":false}}}");

        byte[] bad = V.get("gcm_dev").clone();
        bad[40] ^= 1;
        check("GCM alterado se rechaza", !TuyaCodec.decode(bad, KB, false, true).valid);
        check("GCM con otra clave se rechaza", !TuyaCodec.decode(V.get("gcm_dev"), "fedcba9876543210".getBytes(), false, true).valid);
        bad = V.get("hmac_frame").clone();
        bad[20] ^= 1;
        check("HMAC alterado se rechaza", !TuyaCodec.decode(bad, KB, true, false).valid);
        check("HMAC correcto", TuyaCodec.decode(V.get("hmac_frame"), KB, true, false).valid);
        bad = V.get("crc_plain").clone();
        bad[18] ^= 1;
        check("CRC alterado se rechaza", !TuyaCodec.decode(bad, null, false, false).valid);
        check("trama corta no revienta", !TuyaCodec.decode(new byte[10], KB, false, true).valid);

        // lectura del flujo: dos tramas seguidas, de formatos distintos
        byte[] two = TuyaCodec.concat(V.get("crc_plain"), V.get("gcm_frame"));
        java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(two);
        check("lee 55AA", Arrays.equals(TuyaCodec.readRaw(in), V.get("crc_plain")));
        check("lee 6699", Arrays.equals(TuyaCodec.readRaw(in), V.get("gcm_frame")));
        boolean threw = false;
        try { TuyaCodec.readRaw(new java.io.ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5, 6, 7, 8})); }
        catch (java.io.IOException e) { threw = true; }
        check("prefijo desconocido", threw);
        byte[] huge = V.get("crc_plain").clone();
        huge[12] = 0x7f;
        threw = false;
        try { TuyaCodec.readRaw(new java.io.ByteArrayInputStream(huge)); } catch (java.io.IOException e) { threw = true; }
        check("longitud absurda", threw);

        // claves de sesión
        byte[] ln = "0123456789abcdef".getBytes(), rn = V.get("neg_remote"), x = new byte[16];
        for (int i = 0; i < 16; i++) x[i] = (byte) (ln[i] ^ rn[i]);
        check("clave de sesión 3.4", Arrays.equals(TuyaCodec.ecbEncrypt(KB, x, false), V.get("sess34")));
        check("clave de sesión 3.5", Arrays.equals(Arrays.copyOf(TuyaCodec.gcmEncrypt(KB, Arrays.copyOf(ln, 12), null, x), 16), V.get("sess35")));
        check("HMAC de negociación", Arrays.equals(TuyaCodec.hmac(KB, ln), V.get("neg_hmac_local")));
    }

    static void clientFrames() throws Exception {
        System.out.println("Mensajes del cliente por versión");
        byte[] js = "{\"devId\":\"abc\",\"uid\":\"abc\",\"t\":\"1700000000\",\"dps\":{\"1\":true}}".getBytes();
        check("3.3 control", Arrays.equals(TuyaSession.encode("3.3", TuyaCodec.CONTROL, js, 2, KB, KB), V.get("c33_control")));
        check("3.3 consulta", Arrays.equals(TuyaSession.encode("3.3", TuyaCodec.DP_QUERY, js, 1, KB, KB), V.get("c33_query")));
        check("3.1 control", Arrays.equals(TuyaSession.encode("3.1", TuyaCodec.CONTROL, js, 4, KB, KB), V.get("c31_control")));
        check("3.1 consulta", Arrays.equals(TuyaSession.encode("3.1", TuyaCodec.DP_QUERY, js, 4, KB, KB), V.get("c31_query")));
        check("3.4 control", Arrays.equals(TuyaSession.encode("3.4", TuyaCodec.CONTROL_NEW, js, 8, KB, KB), V.get("c34_control")));
        check("3.4 consulta", Arrays.equals(TuyaSession.encode("3.4", TuyaCodec.DP_QUERY_NEW, "{}".getBytes(), 8, KB, KB), V.get("c34_query")));
        TuyaCodec.Frame f = TuyaCodec.decode(TuyaSession.encode("3.5", TuyaCodec.CONTROL_NEW, js, 8, KB, KB), KB, false, false);
        check("3.5 control", f.valid && TuyaCodec.startsWith(f.payload, "3.5") && f.payload.length == 15 + js.length);
    }

    static Map<String, Object> one(String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    static void session(String v) throws Exception {
        System.out.println("Sesión " + v);
        FakeDevice f = fake(v);
        Rec r = new Rec();
        TuyaSession s = open(f, KEY, v, r);
        check(v + " conecta", waitFor(() -> r.state == TuyaSession.ONLINE, 4000));
        check(v + " estado inicial", waitFor(() -> r.dps.size() == 3, 2000));
        eq(v + " dp1", r.dps.get("1"), false);
        eq(v + " dp2", r.dps.get("2"), true);
        eq(v + " dp9", r.dps.get("9"), 0L);
        check(v + " envía control", s.control(null, one("1", true)));
        check(v + " el dispositivo cambió", waitFor(() -> Boolean.TRUE.equals(f.dps.get("1")), 2000));
        check(v + " llega la confirmación", waitFor(() -> Boolean.TRUE.equals(r.dps.get("1")), 2000));
        s.control(null, one("9", 120L));
        check(v + " temporizador", waitFor(() -> Long.valueOf(120).equals(r.dps.get("9")), 2000));
        f.push(null, one("2", false));
        check(v + " cambio físico", waitFor(() -> Boolean.FALSE.equals(r.dps.get("2")), 2000));
        if (!v.equals("3.1")) {
            s.refresh(Arrays.asList(18, 19, 20));
            check(v + " pide medición", waitFor(() -> f.refreshes == 1, 2000));
        }
        eq(v + " versión", s.workingVersion(), v);
        eq(v + " sin errores", r.errors.size(), 0);
        s.close();
        Thread.sleep(100);
        check(v + " cerrada no envía", !s.control(null, one("1", false)) || !waitFor(() -> Boolean.FALSE.equals(f.dps.get("1")), 300));
        done(s, f);
    }

    static void autoVersion(String v) throws Exception {
        System.out.println("Detección automática " + v);
        FakeDevice f = fake(v);
        Rec r = new Rec();
        TuyaSession s = open(f, KEY, "auto", r);
        check(v + " detectada", waitFor(() -> r.state == TuyaSession.ONLINE && r.dps.size() == 3, 20000));
        eq(v + " versión detectada", s.workingVersion(), v);
        s.control(null, one("1", true));
        check(v + " control tras detectar", waitFor(() -> Boolean.TRUE.equals(r.dps.get("1")), 2000));
        done(s, f);
    }

    static void wrongKey() throws Exception {
        System.out.println("Clave incorrecta");
        for (String v : new String[]{"3.3", "3.4", "3.5"}) {
            FakeDevice f = fake(v);
            Rec r = new Rec();
            TuyaSession s = open(f, "fedcba9876543210", v, r);
            check(v + " no queda en línea", !waitFor(() -> r.state == TuyaSession.ONLINE, 1500));
            check(v + " avisa del fallo", waitFor(() -> !r.errors.isEmpty(), 6000));
            check(v + " lo atribuye a clave o falta de respuesta", !r.errors.contains(TuyaSession.ERR_UNREACHABLE));
            eq(v + " no recibe datos", r.dps.size(), 0);
            done(s, f);
        }
    }

    static void reconnect() throws Exception {
        System.out.println("Reconexión");
        FakeDevice f = fake("3.4");
        Rec r = new Rec();
        TuyaSession s = open(f, KEY, "3.4", r);
        check("en línea", waitFor(() -> r.state == TuyaSession.ONLINE, 4000));
        f.dropConnections();
        check("detecta el corte", waitFor(() -> r.states.contains(TuyaSession.OFFLINE), 3000));
        f.dps.put("1", true);
        check("vuelve sola", waitFor(() -> r.state == TuyaSession.ONLINE && Boolean.TRUE.equals(r.dps.get("1")), 8000));
        check("nueva negociación", f.connections >= 2);
        done(s, f);
    }

    static void device22() throws Exception {
        System.out.println("Equipos «device22»");
        FakeDevice f = fake("3.3");
        f.device22 = true;
        Rec r = new Rec();
        TuyaSession s = open(f, KEY, "3.3", r);
        check("obtiene el estado con la consulta alterna", waitFor(() -> r.dps.size() == 3, 4000));
        s.control(null, one("1", true));
        check("controla", waitFor(() -> Boolean.TRUE.equals(r.dps.get("1")), 2000));
        done(s, f);
    }

    static void hub(String v) throws Exception {
        System.out.println("Hub Zigbee " + v);
        FakeDevice f = new FakeDevice(ID, KEY, v, 0);
        f.children.put("a1b2", new LinkedHashMap<>(one("1", false)));
        Map<String, Object> two = new LinkedHashMap<>();
        two.put("1", true);
        two.put("2", false);
        f.children.put("c3d4", two);
        Rec r = new Rec();
        TuyaSession s = new TuyaSession(f.id, "127.0.0.1", f.port(), KEY, v, r);
        s.setChildren(new HashSet<>(Arrays.asList("a1b2", "c3d4")));
        live.add(s);
        s.start();
        check(v + " hub en línea", waitFor(() -> r.state == TuyaSession.ONLINE, 4000));
        check(v + " estado de los dos hijos", waitFor(() -> r.byCid.size() == 2 && r.byCid.get("c3d4").size() == 2, 3000));
        eq(v + " hijo 1", r.byCid.get("a1b2").get("1"), false);
        s.control("c3d4", one("2", true));
        check(v + " control del hijo", waitFor(() -> Boolean.TRUE.equals(f.children.get("c3d4").get("2")), 2000));
        check(v + " confirmación del hijo", waitFor(() -> Boolean.TRUE.equals(r.byCid.get("c3d4").get("2")), 2000));
        eq(v + " el otro hijo no cambia", f.children.get("a1b2").get("1"), false);
        f.push("a1b2", one("1", true));
        check(v + " cambio físico del hijo", waitFor(() -> Boolean.TRUE.equals(r.byCid.get("a1b2").get("1")), 2000));
        eq(v + " nada propio del hub", r.dps.size(), 0);
        done(s, f);
    }

    static void heartbeat() throws Exception {
        System.out.println("Latido");
        FakeDevice f = fake("3.3");
        Rec r = new Rec();
        TuyaSession s = new TuyaSession(f.id, "127.0.0.1", f.port(), KEY, "3.3", r);
        s.start();
        check("en línea", waitFor(() -> r.state == TuyaSession.ONLINE, 4000));
        long now = System.currentTimeMillis();
        s.tick(now + 10000);
        check("manda latido", waitFor(() -> f.heartbeats == 1, 2000));
        s.tick(now + 12000);
        Thread.sleep(200);
        eq("no lo repite antes de tiempo", f.heartbeats, 1);
        s.tick(now + 60000);
        check("silencio prolongado corta", waitFor(() -> r.states.contains(TuyaSession.OFFLINE), 3000));
        s.close();
        f.close();
    }

    static void unreachable() throws Exception {
        System.out.println("Inalcanzable");
        FakeDevice f = fake("3.3");
        int port = f.port();
        f.close();
        Rec r = new Rec();
        TuyaSession s = new TuyaSession(ID, "127.0.0.1", port, KEY, "auto", r);
        s.start();
        check("avisa", waitFor(() -> r.errors.contains(TuyaSession.ERR_UNREACHABLE), 4000));
        check("sin conexión no controla", !s.control(null, one("1", true)));
        s.close();
    }

    static void discovery() throws Exception {
        System.out.println("Descubrimiento");
        Discovery.Found d = Discovery.parse(V.get("udp33"));
        check("anuncio 3.3", d != null);
        eq("id", d.id, "bf1234567890abcdefghij");
        eq("ip", d.ip, "192.168.1.50");
        eq("versión", d.version, "3.3");
        eq("producto", d.productKey, "keyabc");
        d = Discovery.parse(V.get("udp31"));
        eq("anuncio 3.1", d == null ? null : d.version, "3.1");
        d = Discovery.parse(V.get("udp35"));
        eq("anuncio 3.5", d == null ? null : d.version, "3.5");
        check("basura", Discovery.parse(new byte[64]) == null);
        check("corto", Discovery.parse(new byte[3]) == null);
        byte[] bad = V.get("udp35").clone();
        bad[35] ^= 1;
        check("anuncio alterado", Discovery.parse(bad) == null);

        int port;
        try (DatagramSocket probe = new DatagramSocket(0)) { port = probe.getLocalPort(); }
        List<Discovery.Found> got = new CopyOnWriteArrayList<>();
        Discovery disc = new Discovery(got::add, port);
        disc.start();
        eq("puerto abierto", disc.boundPorts(), 1);
        try (DatagramSocket tx = new DatagramSocket()) {
            byte[] p = V.get("udp33");
            tx.send(new DatagramPacket(p, p.length, new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port)));
            tx.send(new DatagramPacket(new byte[50], 50, new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port)));
        }
        check("recibe por UDP", waitFor(() -> got.size() == 1, 2000));
        disc.stop();
    }

    static void cloud() throws Exception {
        System.out.println("Nube (servidor simulado)");
        eq("firma", CloudClient.sign("cid123", "secreto", "tok456", "1700000000000", "GET",
                "/v1.0/iot-01/associated-users/devices?size=50"), signVector);
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        srv.createContext("/", ex -> {
            String path = ex.getRequestURI().toString();
            seen.add(path);
            String cid = ex.getRequestHeaders().getFirst("client_id");
            String t = ex.getRequestHeaders().getFirst("t");
            String tok = ex.getRequestHeaders().getFirst("access_token");
            String sign = ex.getRequestHeaders().getFirst("sign");
            String body;
            if (!CloudClient.sign("cid123", "secreto", tok, t, "GET", path).equals(sign) || !"cid123".equals(cid)) {
                body = "{\"success\":false,\"code\":1004,\"msg\":\"sign invalid\"}";
            } else if (path.startsWith("/v1.0/token")) {
                body = "{\"success\":true,\"result\":{\"access_token\":\"tok456\",\"expire_time\":7200}}";
            } else if (!"tok456".equals(tok)) {
                body = "{\"success\":false,\"code\":1010,\"msg\":\"token invalid\"}";
            } else if (path.equals("/v1.0/iot-01/associated-users/devices?size=50")) {
                body = "{\"success\":true,\"result\":{\"has_more\":true,\"last_row_key\":\"k2\",\"devices\":["
                        + "{\"id\":\"hub1\",\"name\":\"Hub\",\"local_key\":\"kkkkkkkkkkkkkkkk\",\"category\":\"wg2\",\"sub\":false,\"ip\":\"189.200.1.1\"},"
                        + "{\"id\":\"sw1\",\"name\":\"Sala\",\"local_key\":\"kkkkkkkkkkkkkkkk\",\"category\":\"kg\",\"sub\":true,\"node_id\":\"a1b2\"}]}}";
            } else if (path.equals("/v1.0/iot-01/associated-users/devices?last_row_key=k2&size=50")) {
                body = "{\"success\":true,\"result\":{\"has_more\":false,\"devices\":["
                        + "{\"id\":\"plug1\",\"name\":\"Cafetera\",\"local_key\":\"pppppppppppppppp\",\"category\":\"cz\",\"sub\":false}]}}";
            } else if (path.equals("/v1.0/devices/plug1/specifications")) {
                body = "{\"success\":true,\"result\":{\"category\":\"cz\",\"functions\":[{\"code\":\"switch_1\",\"dp_id\":1,\"type\":\"Boolean\"},"
                        + "{\"code\":\"countdown_1\",\"dp_id\":9,\"type\":\"Integer\"}],\"status\":[{\"code\":\"cur_power\",\"dp_id\":19,\"type\":\"Integer\"}]}}";
            } else {
                body = "{\"success\":false,\"code\":1108,\"msg\":\"uri path invalid\"}";
            }
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        srv.start();
        String base = "http://127.0.0.1:" + srv.getAddress().getPort();
        CloudClient c = new CloudClient(base, " cid123 ", "secreto\n");
        c.login();
        List<Map<String, Object>> devs = c.devices();
        eq("dos páginas", devs.size(), 3);
        List<Device> list = Importer.fromCloud(devs);
        eq("clave", list.get(2).key, "pppppppppppppppp");
        eq("IP pública descartada", list.get(0).ip, "");
        eq("subdispositivo", list.get(1).cid, "a1b2");
        eq("padre por clave compartida", list.get(1).parent, "hub1");
        check("especificaciones", DpLogic.applySpecs(list.get(2), c.specifications("plug1")));
        eq("interruptor", DpLogic.switches(list.get(2)).size(), 1);
        eq("temporizador", DpLogic.countdownFor(list.get(2), list.get(2).dp("1")).id, "9");
        check("especificaciones ausentes", c.specifications("otro") == null);
        boolean threw = false;
        try { new CloudClient(base, "cid123", "malo").login(); } catch (CloudClient.CloudException e) {
            threw = e.getMessage().contains("sign invalid");
        }
        check("secreto incorrecto", threw);
        srv.stop(0);
    }

    static void model() throws Exception {
        System.out.println("Modelo");
        String tiny = "[{\"name\":\"Lámpara\",\"id\":\"d1\",\"key\":\"aaaaaaaaaaaaaaaa\",\"ip\":\"192.168.1.20\",\"version\":\"3.4\","
                + "\"mapping\":{\"1\":{\"code\":\"switch_1\",\"type\":\"Boolean\"},\"2\":{\"code\":\"switch_2\",\"type\":\"Boolean\"},"
                + "\"7\":{\"code\":\"countdown_1\",\"type\":\"Integer\"},\"16\":{\"code\":\"switch_backlight\",\"type\":\"Boolean\"}}},"
                + "{\"name\":\"Hub\",\"id\":\"h1\",\"key\":\"bbbbbbbbbbbbbbbb\",\"ip\":\"8.8.8.8\"},"
                + "{\"name\":\"Pared\",\"id\":\"z1\",\"key\":\"bbbbbbbbbbbbbbbb\",\"sub\":true,\"node_id\":\"00aa\"},"
                + "{\"name\":\"sin id\"}, 5]";
        Importer.Result r = Importer.parse(tiny);
        eq("tres válidos", r.devices.size(), 3);
        Device d1 = r.devices.get(0);
        eq("versión", d1.version, "3.4");
        eq("dos interruptores (sin la luz de fondo)", DpLogic.switches(d1).size(), 2);
        eq("temporizador por código", DpLogic.countdownFor(d1, d1.dp("1")).id, "7");
        check("sin temporizador para el 2", DpLogic.countdownFor(d1, d1.dp("2")) == null);
        eq("IP pública fuera", r.devices.get(1).ip, "");
        eq("versión por defecto", r.devices.get(1).version, "auto");
        eq("padre", r.devices.get(2).parent, "h1");
        boolean threw = false;
        try { Importer.parse("{\"hola\":1}"); } catch (IllegalArgumentException e) { threw = true; }
        check("archivo ajeno", threw);

        File file = File.createTempFile("hogar", ".json");
        file.delete();
        Store st = new Store(file);
        st.load();
        eq("vacío", st.devices.size(), 0);
        int[] n = Importer.merge(st, r);
        eq("agregados", n[0], 3);
        st.rooms.add("Sala");
        st.find("d1").room = "Sala";
        st.find("d1").name = "Mi lámpara";
        st.find("d1").dp("1").name = "Techo";
        n = Importer.merge(st, Importer.parse(tiny.replace("aaaaaaaaaaaaaaaa", "cccccccccccccccc")));
        eq("sin duplicar", n[0], 0);
        eq("actualiza la clave", st.find("d1").key, "cccccccccccccccc");
        eq("respeta el nombre", st.find("d1").name, "Mi lámpara");
        eq("respeta el cuarto", st.find("d1").room, "Sala");
        st.save();
        st.flush();
        Store again = new Store(file);
        again.load();
        eq("persisten", again.devices.size(), 3);
        eq("nombre de DP", again.find("d1").dp("1").name, "Techo");
        eq("cuartos", again.rooms, Arrays.asList("Sala"));
        eq("hijos del hub", again.childrenOf("h1").size(), 1);
        check("exportar sin claves", !Json.write(again.toJson(false)).contains("cccccccc"));
        Importer.Result back = Importer.parse(Json.pretty(again.toJson(true)));
        eq("respaldo: cuartos", back.rooms, Arrays.asList("Sala"));
        eq("respaldo: cuarto del dispositivo", back.devices.get(0).room, "Sala");
        eq("respaldo: subdispositivo", back.devices.get(2).cid, "00aa");

        Files.write(file.toPath(), "{roto".getBytes());
        Store broken = new Store(file);
        broken.load();
        eq("archivo dañado no revienta", broken.devices.size(), 0);
        check("se aparta", new File(file.getPath() + ".bad").exists());
        new File(file.getPath() + ".bad").delete();

        // aprendizaje sin mapeo
        Device p = new Device();
        Map<String, Object> dps = new LinkedHashMap<>();
        dps.put("1", true);
        dps.put("9", 0L);
        dps.put("18", 120L);
        dps.put("19", 235L);
        dps.put("20", 1271L);
        dps.put("38", "memory");
        dps.put("41", false);
        check("aprende", DpLogic.learn(p, dps));
        check("sin cambios la segunda vez", !DpLogic.learn(p, dps));
        p.values.putAll(dps);
        eq("contacto: un interruptor", DpLogic.switches(p).size(), 1);
        eq("contacto: temporizador 9", DpLogic.countdownFor(p, p.dp("1")).id, "9");
        eq("watts", DpLogic.watts(p), 23.5);
        eq("volts", DpLogic.volts(p), 127.1);
        eq("mA", DpLogic.milliamps(p), 120.0);
        eq("ids de medición", DpLogic.metricIds(p), Arrays.asList(18, 19, 20));
        eq("etiqueta", DpLogic.label(p.dp("1"), 0, 1), "Encendido");
        Device w = new Device();
        dps = new LinkedHashMap<>();
        for (int i = 1; i <= 3; i++) { dps.put("" + i, false); dps.put("" + (i + 6), 0L); }
        DpLogic.learn(w, dps);
        eq("pared: tres", DpLogic.switches(w).size(), 3);
        eq("pared: temporizador del 3", DpLogic.countdownFor(w, w.dp("3")).id, "9");
        eq("pared: etiqueta", DpLogic.label(w.dp("2"), 1, 3), "Interruptor 2");
        check("sin medidor", DpLogic.watts(w) == null);
        check("IP local", Importer.isLocalIp("192.168.0.9") && Importer.isLocalIp("10.1.2.3") && Importer.isLocalIp("172.20.0.1"));
        check("IP no local", !Importer.isLocalIp("172.32.0.1") && !Importer.isLocalIp("1.2.3") && !Importer.isLocalIp("a.b.c.d") && !Importer.isLocalIp(null));
        file.delete();
    }
}
