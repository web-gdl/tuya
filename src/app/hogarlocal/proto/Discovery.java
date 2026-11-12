package app.hogarlocal.proto;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/**
 * Escucha los anuncios UDP que los dispositivos Tuya emiten cada pocos segundos:
 * puerto 6666 (3.1, sin cifrar) y 6667 (3.3–3.5, cifrado con una clave fija conocida).
 * Solo escucha; no envía nada a la red.
 */
public final class Discovery {
    public static final class Found {
        public String id, ip, version, productKey;
    }

    public interface Listener {
        void onFound(Found f);
    }

    private final Listener listener;
    private final int[] ports;
    private final DatagramSocket[] sockets;
    private volatile boolean running;
    private volatile int bound;

    public Discovery(Listener listener) { this(listener, 6666, 6667); }

    public Discovery(Listener listener, int... ports) {
        this.listener = listener;
        this.ports = ports;
        this.sockets = new DatagramSocket[ports.length];
    }

    /** Cuántos puertos se pudieron abrir (0 = otra app los tiene ocupados). */
    public int boundPorts() { return bound; }

    public synchronized void start() {
        if (running) return;
        running = true;
        bound = 0;
        for (int i = 0; i < ports.length; i++) {
            try {
                DatagramSocket s = new DatagramSocket(null);
                s.setReuseAddress(true);
                s.setBroadcast(true);
                s.bind(new InetSocketAddress(ports[i]));
                sockets[i] = s;
                bound++;
                Thread t = new Thread(() -> loop(s), "tuya-udp-" + ports[i]);
                t.setDaemon(true);
                t.start();
            } catch (Exception e) {
                sockets[i] = null;
            }
        }
    }

    public synchronized void stop() {
        running = false;
        for (int i = 0; i < sockets.length; i++) {
            if (sockets[i] != null) sockets[i].close();
            sockets[i] = null;
        }
    }

    private void loop(DatagramSocket s) {
        byte[] buf = new byte[2048];
        while (running && !s.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                s.receive(p);
                Found f = parse(Arrays.copyOf(p.getData(), p.getLength()));
                if (f == null) continue;
                if (f.ip == null || f.ip.isEmpty()) f.ip = p.getAddress().getHostAddress();
                listener.onFound(f);
            } catch (Exception e) {
                if (!running || s.isClosed()) return;
            }
        }
    }

    /** Interpreta un anuncio; null si no es de un dispositivo Tuya. */
    public static Found parse(byte[] data) {
        try {
            if (data.length < 24) return null;
            int prefix = ((data[0] & 255) << 24) | ((data[1] & 255) << 16) | ((data[2] & 255) << 8) | (data[3] & 255);
            String json;
            if (prefix == TuyaCodec.PREFIX_6699) {
                TuyaCodec.Frame f = TuyaCodec.decode(data, TuyaCodec.UDP_KEY, false, true);
                if (!f.valid) return null;
                json = new String(f.payload, StandardCharsets.UTF_8);
            } else if (prefix == TuyaCodec.PREFIX_55AA) {
                TuyaCodec.Frame f = TuyaCodec.decode(data, null, false, true);
                if (f.payload == null || f.payload.length == 0) return null;
                if (f.payload[0] == '{') json = new String(f.payload, StandardCharsets.UTF_8);
                else json = new String(TuyaCodec.ecbDecrypt(TuyaCodec.UDP_KEY, f.payload), StandardCharsets.UTF_8);
            } else {
                return null;
            }
            int end = json.lastIndexOf('}');
            if (end < 0) return null;
            Map<String, Object> m = Json.parseObject(json.substring(0, end + 1));
            Found f = new Found();
            f.id = Json.str(m, "gwId", null);
            if (f.id == null) f.id = Json.str(m, "devId", null);
            if (f.id == null || f.id.isEmpty()) return null;
            f.ip = Json.str(m, "ip", null);
            f.version = Json.str(m, "version", null);
            f.productKey = Json.str(m, "productKey", null);
            return f;
        } catch (Exception e) {
            return null;
        }
    }
}
