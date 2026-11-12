package app.hogarlocal.proto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Conexión persistente con un dispositivo Tuya (o un hub) por la red local.
 * Un hilo lee; las escrituras van a una cola de un solo hilo. Se reconecta sola.
 * Los subdispositivos Zigbee se direccionan con su «cid» por la conexión del hub.
 */
public final class TuyaSession {
    public static final int CONNECTING = 0, ONLINE = 1, OFFLINE = 2;

    public static final String ERR_KEY = "clave";
    public static final String ERR_NO_REPLY = "sin_respuesta";
    public static final String ERR_UNREACHABLE = "inalcanzable";

    public interface Listener {
        void onState(TuyaSession s, int state, String error);
        /** @param cid null si los datos son del propio dispositivo */
        void onDps(TuyaSession s, String cid, Map<String, Object> dps);
    }

    private static final String[] AUTO = {"3.3", "3.4", "3.5", "3.1"};
    private static final long FIRST_REPLY_MS = 5000, SILENCE_MS = 30000, HEARTBEAT_MS = 9000;

    public final String devId;
    private final String ip;
    private final int port;
    private final byte[] realKey;
    private final String fixedVersion;
    private final Listener listener;
    private final Set<String> children = ConcurrentHashMap.newKeySet();

    private final Object writeLock = new Object();
    private volatile boolean closed;
    private volatile Socket socket;
    private volatile OutputStream out;
    private volatile String version;
    private volatile byte[] sessionKey;
    private volatile boolean gotReply;
    private volatile boolean device22;
    private volatile long connectedAt, lastRx, lastHeartbeat;
    private volatile int state = OFFLINE;
    private volatile String pendingError;
    private int seq = 1;
    private int autoIdx;
    private boolean everOnline;
    private Thread thread;
    private ExecutorService writer;

    public TuyaSession(String devId, String ip, int port, String key, String version, Listener listener) {
        this.devId = devId;
        this.ip = ip;
        this.port = port;
        this.realKey = key.getBytes(StandardCharsets.ISO_8859_1);
        this.fixedVersion = isVersion(version) ? version : null;
        this.listener = listener;
    }

    public static boolean isVersion(String v) {
        return "3.1".equals(v) || "3.2".equals(v) || "3.3".equals(v) || "3.4".equals(v) || "3.5".equals(v);
    }

    public String ip() { return ip; }
    public int state() { return state; }
    /** Versión con la que se logró hablar; null si aún no hay respuesta. */
    public String workingVersion() { return everOnline ? version : null; }

    public void setChildren(Set<String> cids) {
        for (String c : cids) {
            if (children.add(c) && state == ONLINE) query(c);
        }
        children.retainAll(cids);
    }

    public synchronized void start() {
        if (thread != null || closed) return;
        writer = new ThreadPoolExecutor(0, 1, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                r -> new Thread(r, "tuya-w-" + devId));
        thread = new Thread(this::run, "tuya-r-" + devId);
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void close() {
        closed = true;
        closeSocket();
        if (thread != null) thread.interrupt();
        if (writer != null) writer.shutdownNow();
    }

    private void closeSocket() {
        Socket s = socket;
        if (s == null) return;
        try {
            s.close();
        } catch (IOException ignored) {
        } catch (RuntimeException e) {
            // Android no deja tocar la red desde el hilo principal: se cierra aparte.
            new Thread(() -> {
                try { s.close(); } catch (IOException ignored) { }
            }).start();
        }
    }

    /** Llamar más o menos cada segundo: vigila silencios y manda latidos. */
    public void tick(long now) {
        if (closed || socket == null || out == null) return;
        if (!gotReply) {
            if (now - connectedAt > FIRST_REPLY_MS) { pendingError = ERR_NO_REPLY; closeSocket(); }
            return;
        }
        if (now - lastRx > SILENCE_MS) { pendingError = ERR_NO_REPLY; closeSocket(); return; }
        if (now - lastHeartbeat >= HEARTBEAT_MS) {
            lastHeartbeat = now;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("gwId", devId);
            m.put("devId", devId);
            post(TuyaCodec.HEART_BEAT, m);
        }
    }

    // ---- órdenes ----

    public boolean control(String cid, Map<String, Object> dps) {
        if (state != ONLINE) return false;
        Map<String, Object> m = new LinkedHashMap<>();
        long t = System.currentTimeMillis() / 1000;
        if (isNew()) {
            Map<String, Object> data = new LinkedHashMap<>();
            if (cid != null) { data.put("cid", cid); data.put("ctype", 0); }
            data.put("dps", dps);
            m.put("protocol", 5);
            m.put("t", t);
            m.put("data", data);
            return post(TuyaCodec.CONTROL_NEW, m);
        }
        if (cid != null) {
            m.put("t", t);
            m.put("cid", cid);
        } else {
            m.put("devId", devId);
            m.put("uid", devId);
            m.put("t", String.valueOf(t));
        }
        m.put("dps", dps);
        return post(TuyaCodec.CONTROL, m);
    }

    public boolean query(String cid) {
        Map<String, Object> m = new LinkedHashMap<>();
        long t = System.currentTimeMillis() / 1000;
        if (isNew()) {
            if (cid != null) m.put("cid", cid);
            return post(TuyaCodec.DP_QUERY_NEW, m);
        }
        if (cid != null) {
            m.put("t", t);
            m.put("cid", cid);
            return post(TuyaCodec.DP_QUERY, m);
        }
        if (device22) {
            Map<String, Object> dps = new LinkedHashMap<>();
            dps.put("1", null);
            m.put("devId", devId);
            m.put("uid", devId);
            m.put("t", String.valueOf(t));
            m.put("dps", dps);
            return post(TuyaCodec.CONTROL_NEW, m);
        }
        m.put("gwId", devId);
        m.put("devId", devId);
        m.put("uid", devId);
        m.put("t", String.valueOf(t));
        return post(TuyaCodec.DP_QUERY, m);
    }

    /** Pide al dispositivo que vuelva a medir (consumo, voltaje…). No existe en 3.1. */
    public boolean refresh(List<Integer> dpIds) {
        if (state != ONLINE || "3.1".equals(version)) return false;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dpId", dpIds);
        return post(TuyaCodec.UPDATEDPS, m);
    }

    private boolean isNew() { return "3.4".equals(version) || "3.5".equals(version); }

    private boolean post(int cmd, Map<String, Object> json) {
        ExecutorService w = writer;
        if (w == null || out == null) return false;
        byte[] payload = Json.write(json).getBytes(StandardCharsets.UTF_8);
        try {
            w.execute(() -> {
                try {
                    send(cmd, payload);
                } catch (Exception e) {
                    closeSocket();
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    private static boolean noHeader(int cmd) {
        return cmd == TuyaCodec.DP_QUERY || cmd == TuyaCodec.DP_QUERY_NEW || cmd == TuyaCodec.UPDATEDPS
                || cmd == TuyaCodec.HEART_BEAT || cmd == TuyaCodec.NEG_START || cmd == TuyaCodec.NEG_RESP
                || cmd == TuyaCodec.NEG_FINISH || cmd == TuyaCodec.LAN_EXT_STREAM;
    }

    private static byte[] versionHeader(String v) {
        byte[] h = new byte[15];
        System.arraycopy(v.getBytes(StandardCharsets.US_ASCII), 0, h, 0, 3);
        return h;
    }

    private void send(int cmd, byte[] payload) throws IOException, GeneralSecurityException {
        synchronized (writeLock) {
            OutputStream o = out;
            if (o == null) throw new IOException("sin conexión");
            o.write(encode(version, cmd, payload, seq++, sessionKey != null ? sessionKey : realKey, realKey));
            o.flush();
        }
    }

    /** Cifra y empaqueta un mensaje del cliente según la versión del protocolo. */
    public static byte[] encode(String v, int cmd, byte[] payload, int seq, byte[] key, byte[] realKey)
            throws GeneralSecurityException {
        if ("3.5".equals(v)) {
            if (!noHeader(cmd)) payload = TuyaCodec.concat(versionHeader(v), payload);
            return TuyaCodec.pack66(seq, cmd, payload, key, null);
        }
        if ("3.4".equals(v)) {
            if (!noHeader(cmd)) payload = TuyaCodec.concat(versionHeader(v), payload);
            return TuyaCodec.pack55(seq, cmd, TuyaCodec.ecbEncrypt(key, payload, true), key, null);
        }
        if ("3.1".equals(v)) {
            if (cmd == TuyaCodec.CONTROL) {
                byte[] b64 = Base64.getEncoder().encode(TuyaCodec.ecbEncrypt(realKey, payload, true));
                byte[] pre = TuyaCodec.concat("data=".getBytes(StandardCharsets.US_ASCII), b64,
                        "||lpv=3.1||".getBytes(StandardCharsets.US_ASCII), realKey);
                String sig = TuyaCodec.hex(TuyaCodec.md5(pre)).substring(8, 24);
                payload = TuyaCodec.concat(("3.1" + sig).getBytes(StandardCharsets.US_ASCII), b64);
            }
            return TuyaCodec.pack55(seq, cmd, payload, null, null);
        }
        // 3.2 y 3.3
        byte[] enc = TuyaCodec.ecbEncrypt(realKey, payload, true);
        if (!noHeader(cmd)) enc = TuyaCodec.concat(versionHeader(v), enc);
        return TuyaCodec.pack55(seq, cmd, enc, null, null);
    }

    // ---- hilo de conexión ----

    private void setState(int st, String error) {
        state = st;
        try {
            listener.onState(this, st, error);
        } catch (RuntimeException ignored) { }
    }

    private void run() {
        int failures = 0;
        while (!closed) {
            version = fixedVersion != null ? fixedVersion : AUTO[autoIdx];
            String error = null;
            boolean wasOnline = false;
            try {
                setState(CONNECTING, null);
                connectOnce();
                wasOnline = gotReply;
            } catch (KeyException e) {
                error = ERR_KEY;
                wasOnline = gotReply;
            } catch (Exception e) {
                wasOnline = gotReply;
                error = pendingError != null ? pendingError : (socket == null || out == null ? ERR_UNREACHABLE : ERR_NO_REPLY);
            } finally {
                out = null;
                closeSocket();
                socket = null;
            }
            if (closed) break;
            setState(OFFLINE, error);
            failures = wasOnline ? 0 : failures + 1;
            long wait;
            boolean keyIsWrong = ERR_KEY.equals(error) && !isNew();
            if (fixedVersion == null && !everOnline && !ERR_UNREACHABLE.equals(error) && !keyIsWrong) {
                // Versión desconocida: probar la siguiente enseguida.
                autoIdx = (autoIdx + 1) % AUTO.length;
                wait = autoIdx == 0 ? 4000 : 400;
            } else {
                wait = Math.min(15000, 1500L << Math.min(failures, 4));
            }
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) { break; }
        }
    }

    private static final class KeyException extends Exception { }

    private void connectOnce() throws Exception {
        pendingError = null;
        gotReply = false;
        sessionKey = null;
        Socket s = new Socket();
        s.setTcpNoDelay(true);
        s.setKeepAlive(true);
        s.connect(new InetSocketAddress(ip, port), 4000);
        socket = s;
        InputStream in = s.getInputStream();
        synchronized (writeLock) { seq = 1; }
        connectedAt = System.currentTimeMillis();
        OutputStream o = s.getOutputStream();
        if (isNew()) {
            s.setSoTimeout(5000);
            negotiate(in, o);
            s.setSoTimeout(0);
        }
        out = o;
        lastHeartbeat = connectedAt = System.currentTimeMillis();
        // La consulta directa confirma el enlace; los hubs responden aunque sea con un error.
        byte[] q = queryPayloadSelf();
        send(queryCmdSelf(), q);
        for (String cid : children) queryDirect(cid);

        while (!closed) {
            byte[] raw = TuyaCodec.readRaw(in);
            boolean hmac = "3.4".equals(version);
            byte[] key = sessionKey != null ? sessionKey : realKey;
            TuyaCodec.Frame f = TuyaCodec.decode(raw, key, hmac, true);
            if (!f.valid) {
                if (f.prefix == TuyaCodec.PREFIX_6699 || hmac) throw new KeyException();
                continue;
            }
            String text = decodePayload(f.payload);
            lastRx = System.currentTimeMillis();
            if (!gotReply) {
                gotReply = true;
                everOnline = true;
                state = ONLINE;
                setState(ONLINE, null);
            }
            if (text != null) handle(text);
        }
    }

    private int queryCmdSelf() {
        if (isNew()) return TuyaCodec.DP_QUERY_NEW;
        return device22 ? TuyaCodec.CONTROL_NEW : TuyaCodec.DP_QUERY;
    }

    private byte[] queryPayloadSelf() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!isNew()) {
            String t = String.valueOf(System.currentTimeMillis() / 1000);
            if (device22) {
                Map<String, Object> dps = new LinkedHashMap<>();
                dps.put("1", null);
                m.put("devId", devId);
                m.put("uid", devId);
                m.put("t", t);
                m.put("dps", dps);
            } else {
                m.put("gwId", devId);
                m.put("devId", devId);
                m.put("uid", devId);
                m.put("t", t);
            }
        }
        return Json.write(m).getBytes(StandardCharsets.UTF_8);
    }

    private void queryDirect(String cid) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        if (isNew()) {
            m.put("cid", cid);
            send(TuyaCodec.DP_QUERY_NEW, Json.write(m).getBytes(StandardCharsets.UTF_8));
        } else {
            m.put("t", System.currentTimeMillis() / 1000);
            m.put("cid", cid);
            send(TuyaCodec.DP_QUERY, Json.write(m).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Negociación de clave de sesión de 3.4 y 3.5. */
    private void negotiate(InputStream in, OutputStream o) throws Exception {
        boolean v35 = "3.5".equals(version);
        byte[] local = TuyaCodec.random(16);
        o.write(encode(version, TuyaCodec.NEG_START, local, seq++, realKey, realKey));
        o.flush();
        TuyaCodec.Frame f;
        try {
            f = TuyaCodec.decode(TuyaCodec.readRaw(in), realKey, !v35, true);
        } catch (IOException e) {
            pendingError = ERR_NO_REPLY;
            throw e;
        }
        if (!f.valid || f.cmd != TuyaCodec.NEG_RESP) throw new KeyException();
        byte[] p = f.payload;
        if (!v35) {
            try {
                p = TuyaCodec.ecbDecrypt(realKey, p);
            } catch (GeneralSecurityException e) { throw new KeyException(); }
        }
        if (p.length < 48) throw new KeyException();
        byte[] remote = Arrays.copyOfRange(p, 0, 16);
        if (!MessageDigest.isEqual(TuyaCodec.hmac(realKey, local), Arrays.copyOfRange(p, 16, 48))) {
            throw new KeyException();
        }
        o.write(encode(version, TuyaCodec.NEG_FINISH, TuyaCodec.hmac(realKey, remote), seq++, realKey, realKey));
        o.flush();
        byte[] x = new byte[16];
        for (int i = 0; i < 16; i++) x[i] = (byte) (local[i] ^ remote[i]);
        if (v35) {
            byte[] enc = TuyaCodec.gcmEncrypt(realKey, Arrays.copyOf(local, 12), null, x);
            sessionKey = Arrays.copyOf(enc, 16);
        } else {
            sessionKey = TuyaCodec.ecbEncrypt(realKey, x, false);
        }
    }

    /** Quita cabeceras y descifra el contenido; null si viene vacío. */
    private String decodePayload(byte[] p) throws KeyException {
        if (p == null || p.length == 0) return null;
        try {
            if ("3.5".equals(version)) {
                if (TuyaCodec.startsWith(p, "3.5")) p = Arrays.copyOfRange(p, 15, p.length);
            } else if ("3.4".equals(version)) {
                p = TuyaCodec.ecbDecrypt(sessionKey, p);
                if (TuyaCodec.startsWith(p, "3.4")) p = Arrays.copyOfRange(p, 15, p.length);
            } else if (TuyaCodec.startsWith(p, "3.1")) {
                if (p.length < 19) return null;
                p = TuyaCodec.ecbDecrypt(realKey, Base64.getDecoder().decode(Arrays.copyOfRange(p, 19, p.length)));
            } else if (!"3.1".equals(version)) {
                if (TuyaCodec.startsWith(p, version) || (p.length & 15) != 0) {
                    if (p.length < 15) return null;
                    p = Arrays.copyOfRange(p, 15, p.length);
                }
                if (p.length == 0) return null;
                p = TuyaCodec.ecbDecrypt(realKey, p);
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new KeyException();
        }
        return new String(p, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private void handle(String text) {
        text = text.trim();
        if (!text.startsWith("{")) {
            // Algunos equipos 3.3 solo aceptan la consulta con la lista de DPS («device22»).
            if (text.contains("data unvalid") && !device22 && !isNew() && children.isEmpty()) {
                device22 = true;
                query(null);
            }
            return;
        }
        Map<String, Object> root;
        try {
            root = Json.parseObject(text);
        } catch (IllegalArgumentException e) { return; }
        Map<String, Object> data = Json.obj(root, "data");
        Map<String, Object> dps = Json.obj(root, "dps");
        if (dps == null && data != null) dps = Json.obj(data, "dps");
        if (dps == null || dps.isEmpty()) return;
        String cid = Json.str(root, "cid", null);
        if (cid == null && data != null) cid = Json.str(data, "cid", null);
        if (cid != null && cid.isEmpty()) cid = null;
        try {
            listener.onDps(this, cid, dps);
        } catch (RuntimeException ignored) { }
    }
}
