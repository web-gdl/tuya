import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import app.hogarlocal.proto.Json;
import app.hogarlocal.proto.TuyaCodec;

/**
 * Dispositivo Tuya simulado (lado servidor del protocolo local 3.1, 3.3, 3.4 y 3.5).
 * Se valida aparte contra tinytuya, la implementación de referencia en Python.
 */
public final class FakeDevice {
    public final String id, version;
    public final byte[] key;
    public final Map<String, Object> dps = new LinkedHashMap<>();
    public final Map<String, Map<String, Object>> children = new LinkedHashMap<>();
    public boolean device22;
    public volatile int heartbeats, controls, queries, refreshes, connections;

    private final ServerSocket server;
    private final List<Conn> conns = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    public FakeDevice(String id, String key, String version, int port) throws IOException {
        this.id = id;
        this.key = key.getBytes(StandardCharsets.ISO_8859_1);
        this.version = version;
        this.server = new ServerSocket(port, 10, InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(this::acceptLoop, "fake-" + id);
        t.setDaemon(true);
        t.start();
    }

    public int port() { return server.getLocalPort(); }

    public void close() {
        closed = true;
        try { server.close(); } catch (IOException ignored) { }
        dropConnections();
    }

    public void dropConnections() {
        for (Conn c : conns) c.shut();
    }

    /** Envía un cambio de estado espontáneo (como al pulsar el botón físico). */
    public void push(String cid, Map<String, Object> changed) {
        synchronized (this) {
            (cid == null ? dps : children.get(cid)).putAll(changed);
        }
        for (Conn c : conns) {
            try { c.status(cid, changed); } catch (Exception ignored) { }
        }
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket s = server.accept();
                connections++;
                Conn c = new Conn(s);
                conns.add(c);
                Thread t = new Thread(c, "fake-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) { return; }
        }
    }

    private final class Conn implements Runnable {
        final Socket s;
        OutputStream out;
        byte[] session;
        int seq = 1;
        boolean ready;

        Conn(Socket s) { this.s = s; }

        void shut() {
            try { s.close(); } catch (IOException ignored) { }
        }

        boolean v34() { return version.equals("3.4"); }
        boolean v35() { return version.equals("3.5"); }

        synchronized void reply(int cmd, int reqSeq, byte[] plain) throws Exception {
            byte[] k = session != null ? session : key;
            byte[] raw;
            if (v35()) {
                raw = TuyaCodec.pack66(seq++, cmd, plain, k, 0);
            } else if (v34()) {
                raw = TuyaCodec.pack55(reqSeq, cmd, TuyaCodec.ecbEncrypt(k, plain, true), k, 0);
            } else {
                raw = TuyaCodec.pack55(reqSeq, cmd, plain, null, 0);
            }
            out.write(raw);
            out.flush();
        }

        byte[] hdr() {
            byte[] h = new byte[15];
            System.arraycopy(version.getBytes(StandardCharsets.US_ASCII), 0, h, 0, 3);
            return h;
        }

        /** Empaqueta JSON como lo hace cada versión en respuestas a consultas. */
        byte[] body(String json, boolean withHeader) throws Exception {
            byte[] j = json.getBytes(StandardCharsets.UTF_8);
            if (version.equals("3.1")) {
                if (!withHeader) return j;
                byte[] b64 = Base64.getEncoder().encode(TuyaCodec.ecbEncrypt(key, j, true));
                return TuyaCodec.concat("3.1".getBytes(), "0123456789abcdef".getBytes(), b64);
            }
            if (v34() || v35()) return withHeader ? TuyaCodec.concat(hdr(), j) : j;
            byte[] enc = TuyaCodec.ecbEncrypt(key, j, true);
            return withHeader ? TuyaCodec.concat(hdr(), enc) : enc;
        }

        void status(String cid, Map<String, Object> changed) throws Exception {
            if (!ready) return;
            Map<String, Object> m = new LinkedHashMap<>();
            if (v34() || v35()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("dps", changed);
                if (cid != null) data.put("cid", cid);
                m.put("protocol", 4);
                m.put("t", System.currentTimeMillis() / 1000);
                m.put("data", data);
            } else {
                m.put("devId", id);
                m.put("dps", changed);
                if (cid != null) m.put("cid", cid);
                m.put("t", System.currentTimeMillis() / 1000);
            }
            reply(TuyaCodec.STATUS, 0, body(Json.write(m), true));
        }

        @Override public void run() {
            try {
                InputStream in = s.getInputStream();
                out = s.getOutputStream();
                if (v34() || v35()) negotiate(in);
                ready = true;
                while (!closed) {
                    byte[] raw = TuyaCodec.readRaw(in);
                    TuyaCodec.Frame f = TuyaCodec.decode(raw, session != null ? session : key, v34(), false);
                    if (!f.valid) throw new IOException("trama inválida");
                    handle(f);
                }
            } catch (Exception e) {
                // conexión terminada
            } finally {
                conns.remove(this);
                shut();
            }
        }

        void negotiate(InputStream in) throws Exception {
            TuyaCodec.Frame f = TuyaCodec.decode(TuyaCodec.readRaw(in), key, v34(), false);
            if (!f.valid || f.cmd != TuyaCodec.NEG_START) throw new IOException("negociación");
            byte[] local = v34() ? TuyaCodec.ecbDecrypt(key, f.payload) : f.payload;
            byte[] remote = TuyaCodec.random(16);
            reply(TuyaCodec.NEG_RESP, f.seq, TuyaCodec.concat(remote, TuyaCodec.hmac(key, local)));
            f = TuyaCodec.decode(TuyaCodec.readRaw(in), key, v34(), false);
            if (!f.valid || f.cmd != TuyaCodec.NEG_FINISH) throw new IOException("negociación 2");
            byte[] fin = v34() ? TuyaCodec.ecbDecrypt(key, f.payload) : f.payload;
            if (!MessageDigest.isEqual(fin, TuyaCodec.hmac(key, remote))) throw new IOException("hmac");
            byte[] x = new byte[16];
            for (int i = 0; i < 16; i++) x[i] = (byte) (local[i] ^ remote[i]);
            session = v35()
                    ? Arrays.copyOf(TuyaCodec.gcmEncrypt(key, Arrays.copyOf(local, 12), null, x), 16)
                    : TuyaCodec.ecbEncrypt(key, x, false);
        }

        byte[] strip(byte[] p) {
            return TuyaCodec.startsWith(p, version) ? Arrays.copyOfRange(p, 15, p.length) : p;
        }

        String plain(TuyaCodec.Frame f) throws Exception {
            byte[] p = f.payload;
            if (p.length == 0) return "";
            if (v35()) return new String(strip(p), StandardCharsets.UTF_8);
            if (v34()) return new String(strip(TuyaCodec.ecbDecrypt(session, p)), StandardCharsets.UTF_8);
            if (version.equals("3.1")) {
                if (!TuyaCodec.startsWith(p, "3.1")) return new String(p, StandardCharsets.UTF_8);
                byte[] b64 = Arrays.copyOfRange(p, 19, p.length);
                String want = TuyaCodec.hex(TuyaCodec.md5(TuyaCodec.concat("data=".getBytes(), b64,
                        "||lpv=3.1||".getBytes(), key))).substring(8, 24);
                if (!want.equals(new String(p, 3, 16, StandardCharsets.US_ASCII))) throw new IOException("firma 3.1");
                return new String(TuyaCodec.ecbDecrypt(key, Base64.getDecoder().decode(b64)), StandardCharsets.UTF_8);
            }
            return new String(TuyaCodec.ecbDecrypt(key, strip(p)), StandardCharsets.UTF_8);
        }

        @SuppressWarnings("unchecked")
        void handle(TuyaCodec.Frame f) throws Exception {
            String text = plain(f);
            Map<String, Object> m = text.startsWith("{") ? Json.parseObject(text) : new LinkedHashMap<>();
            if (text.contains(" ")) throw new IOException("JSON con espacios");
            boolean neu = v34() || v35();
            Map<String, Object> data = Json.obj(m, "data");
            String cid = Json.str(neu && data != null ? data : m, "cid", null);
            switch (f.cmd) {
                case TuyaCodec.HEART_BEAT:
                    heartbeats++;
                    reply(f.cmd, f.seq, new byte[0]);
                    return;
                case TuyaCodec.UPDATEDPS:
                    refreshes++;
                    reply(f.cmd, f.seq, new byte[0]);
                    return;
                case TuyaCodec.DP_QUERY:
                case TuyaCodec.DP_QUERY_NEW:
                    if (neu != (f.cmd == TuyaCodec.DP_QUERY_NEW)) throw new IOException("consulta de otra versión");
                    queries++;
                    if (device22 || (cid == null && !children.isEmpty())) {
                        reply(f.cmd, f.seq, body("json obj data unvalid", false));
                        return;
                    }
                    reply(f.cmd, f.seq, body(state(cid), false));
                    return;
                case TuyaCodec.CONTROL:
                case TuyaCodec.CONTROL_NEW: {
                    Map<String, Object> set = Json.obj(neu ? data : m, "dps");
                    if (set == null) throw new IOException("sin dps");
                    if (device22 && f.cmd == TuyaCodec.CONTROL_NEW && set.containsValue(null)) {
                        queries++;
                        reply(f.cmd, f.seq, body(state(null), false));
                        return;
                    }
                    if (neu != (f.cmd == TuyaCodec.CONTROL_NEW)) throw new IOException("control de otra versión");
                    if (neu && !Long.valueOf(5).equals(m.get("protocol"))) throw new IOException("protocol");
                    controls++;
                    synchronized (FakeDevice.this) {
                        Map<String, Object> target = cid == null ? dps : children.get(cid);
                        if (target == null) throw new IOException("cid desconocido");
                        target.putAll(set);
                    }
                    reply(f.cmd, f.seq, new byte[0]);
                    status(cid, set);
                    return;
                }
                default:
                    throw new IOException("comando " + f.cmd);
            }
        }

        String state(String cid) {
            Map<String, Object> m = new LinkedHashMap<>();
            synchronized (FakeDevice.this) {
                Map<String, Object> src = cid == null ? dps : children.get(cid);
                if (src == null) return "json obj data unvalid";
                if (v34() || v35()) {
                    m.put("dps", new LinkedHashMap<>(src));
                    if (cid != null) m.put("cid", cid);
                } else {
                    m.put("devId", id);
                    m.put("dps", new LinkedHashMap<>(src));
                    if (cid != null) m.put("cid", cid);
                }
            }
            return Json.write(m);
        }
    }

    /** Uso: FakeDevice <versión> <puerto> — para validarlo contra tinytuya. */
    public static void main(String[] a) throws Exception {
        FakeDevice d = new FakeDevice("eb0123456789abcdefghij", "0123456789abcdef", a[0], Integer.parseInt(a[1]));
        d.dps.put("1", false);
        d.dps.put("2", true);
        d.dps.put("9", 0L);
        System.out.println("listo " + d.port());
        Thread.sleep(600000);
    }
}
