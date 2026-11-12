package app.hogarlocal.proto;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Tramas del protocolo local de Tuya (puerto TCP 6668).
 * Formato 55AA: prefijo, secuencia, comando, longitud, [código], datos, CRC32 o HMAC-SHA256, sufijo.
 * Formato 6699 (3.5): prefijo, 2 bytes, secuencia, comando, longitud, IV(12), cifrado GCM, etiqueta(16), sufijo.
 */
public final class TuyaCodec {
    public static final int PREFIX_55AA = 0x000055AA, SUFFIX_55AA = 0x0000AA55;
    public static final int PREFIX_6699 = 0x00006699, SUFFIX_6699 = 0x00009966;

    public static final int NEG_START = 3, NEG_RESP = 4, NEG_FINISH = 5;
    public static final int CONTROL = 7, STATUS = 8, HEART_BEAT = 9, DP_QUERY = 0x0a;
    public static final int CONTROL_NEW = 0x0d, DP_QUERY_NEW = 0x10, UPDATEDPS = 0x12, LAN_EXT_STREAM = 0x40;

    /** Clave fija con la que los dispositivos cifran su anuncio UDP. */
    public static final byte[] UDP_KEY = md5("yGAdlopoPVldABfn".getBytes(StandardCharsets.US_ASCII));

    private static final int MAX_FRAME = 8192;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TuyaCodec() { }

    public static final class Frame {
        public int prefix, seq, cmd, retcode;
        public byte[] payload;
        /** CRC, HMAC o etiqueta GCM correctos. */
        public boolean valid;
    }

    // ---- criptografía ----

    public static byte[] md5(byte[] d) {
        try {
            return MessageDigest.getInstance("MD5").digest(d);
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    public static byte[] sha256(byte[] d) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(d);
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    public static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            return m.doFinal(data);
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    public static byte[] ecbEncrypt(byte[] key, byte[] data, boolean pad) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance(pad ? "AES/ECB/PKCS5Padding" : "AES/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return c.doFinal(data);
    }

    public static byte[] ecbDecrypt(byte[] key, byte[] data) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
        return c.doFinal(data);
    }

    /** Devuelve cifrado + etiqueta de 16 bytes. */
    public static byte[] gcmEncrypt(byte[] key, byte[] iv, byte[] aad, byte[] plain) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        if (aad != null) c.updateAAD(aad);
        return c.doFinal(plain);
    }

    public static byte[] gcmDecrypt(byte[] key, byte[] iv, byte[] aad, byte[] cipherAndTag) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        if (aad != null) c.updateAAD(aad);
        return c.doFinal(cipherAndTag);
    }

    public static byte[] random(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    public static String hex(byte[] d) {
        StringBuilder b = new StringBuilder(d.length * 2);
        for (byte x : d) b.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
        return b.toString();
    }

    public static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] x : parts) n += x.length;
        byte[] out = new byte[n];
        int o = 0;
        for (byte[] x : parts) { System.arraycopy(x, 0, out, o, x.length); o += x.length; }
        return out;
    }

    public static boolean startsWith(byte[] d, String ascii) {
        if (d.length < ascii.length()) return false;
        for (int i = 0; i < ascii.length(); i++) if (d[i] != (byte) ascii.charAt(i)) return false;
        return true;
    }

    // ---- empaquetado ----

    /**
     * @param hmacKey null → CRC32 (3.1–3.3); con clave → HMAC-SHA256 (3.4)
     * @param retcode null en mensajes del cliente; los dispositivos lo incluyen
     */
    public static byte[] pack55(int seq, int cmd, byte[] payload, byte[] hmacKey, Integer retcode) {
        int tail = hmacKey != null ? 36 : 8;
        int len = payload.length + tail + (retcode != null ? 4 : 0);
        ByteBuffer b = ByteBuffer.allocate(16 + len);
        b.putInt(PREFIX_55AA).putInt(seq).putInt(cmd).putInt(len);
        if (retcode != null) b.putInt(retcode);
        b.put(payload);
        byte[] head = Arrays.copyOf(b.array(), b.position());
        if (hmacKey != null) {
            b.put(hmac(hmacKey, head));
        } else {
            CRC32 crc = new CRC32();
            crc.update(head, 0, head.length);
            b.putInt((int) crc.getValue());
        }
        b.putInt(SUFFIX_55AA);
        return b.array();
    }

    public static byte[] pack66(int seq, int cmd, byte[] payload, byte[] key, Integer retcode) throws GeneralSecurityException {
        return pack66(seq, cmd, payload, key, retcode, random(12));
    }

    public static byte[] pack66(int seq, int cmd, byte[] payload, byte[] key, Integer retcode, byte[] iv)
            throws GeneralSecurityException {
        byte[] plain = payload;
        if (retcode != null) plain = concat(ByteBuffer.allocate(4).putInt(retcode).array(), payload);
        int len = 12 + plain.length + 16;
        ByteBuffer b = ByteBuffer.allocate(18 + len + 4);
        b.putInt(PREFIX_6699).putShort((short) 0).putInt(seq).putInt(cmd).putInt(len);
        byte[] aad = Arrays.copyOfRange(b.array(), 4, 18);
        b.put(iv).put(gcmEncrypt(key, iv, aad, plain));
        b.putInt(SUFFIX_6699);
        return b.array();
    }

    // ---- lectura ----

    private static void readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        while (len > 0) {
            int n = in.read(buf, off, len);
            if (n < 0) throw new EOFException("conexión cerrada");
            off += n;
            len -= n;
        }
    }

    /** Lee una trama completa (55AA o 6699) del flujo, sin descifrarla. */
    public static byte[] readRaw(InputStream in) throws IOException {
        byte[] head = new byte[18];
        readFully(in, head, 0, 4);
        int prefix = ByteBuffer.wrap(head).getInt();
        int headLen, extra;
        if (prefix == PREFIX_55AA) { headLen = 16; extra = 0; }
        else if (prefix == PREFIX_6699) { headLen = 18; extra = 4; }
        else throw new IOException("prefijo desconocido " + Integer.toHexString(prefix));
        readFully(in, head, 4, headLen - 4);
        int len = ByteBuffer.wrap(head, headLen - 4, 4).getInt();
        if (len < 8 || len > MAX_FRAME) throw new IOException("longitud inválida " + len);
        byte[] raw = new byte[headLen + len + extra];
        System.arraycopy(head, 0, raw, 0, headLen);
        readFully(in, raw, headLen, len + extra);
        return raw;
    }

    /**
     * Descifra el sobre de una trama.
     * @param key clave para HMAC (55AA con useHmac) o GCM (6699); puede ser null en 55AA con CRC
     * @param fromDevice las tramas del dispositivo llevan un código de retorno de 4 bytes
     */
    public static Frame decode(byte[] raw, byte[] key, boolean useHmac, boolean fromDevice) {
        Frame f = new Frame();
        if (raw.length < 24) return f;
        ByteBuffer b = ByteBuffer.wrap(raw);
        f.prefix = b.getInt();
        if (f.prefix == PREFIX_55AA) {
            f.seq = b.getInt();
            f.cmd = b.getInt();
            int len = b.getInt();
            int tail = useHmac ? 36 : 8;
            if (len < tail || 16 + len > raw.length) return f;
            int bodyEnd = 16 + len - tail;
            if (useHmac) {
                if (key == null) return f;
                byte[] want = hmac(key, Arrays.copyOf(raw, bodyEnd));
                f.valid = MessageDigest.isEqual(want, Arrays.copyOfRange(raw, bodyEnd, bodyEnd + 32));
            } else {
                CRC32 crc = new CRC32();
                crc.update(raw, 0, bodyEnd);
                f.valid = (int) crc.getValue() == ByteBuffer.wrap(raw, bodyEnd, 4).getInt();
            }
            int start = 16;
            // El código de retorno es un entero pequeño; los datos empiezan por «3.x», «{» o cifrado.
            if (fromDevice && bodyEnd - start >= 4 && raw[16] == 0 && raw[17] == 0 && raw[18] == 0) {
                f.retcode = ByteBuffer.wrap(raw, 16, 4).getInt();
                start = 20;
            }
            f.payload = Arrays.copyOfRange(raw, start, bodyEnd);
        } else if (f.prefix == PREFIX_6699) {
            b.getShort();
            f.seq = b.getInt();
            f.cmd = b.getInt();
            int len = b.getInt();
            if (len < 28 || 18 + len > raw.length || key == null) return f;
            byte[] iv = Arrays.copyOfRange(raw, 18, 30);
            byte[] aad = Arrays.copyOfRange(raw, 4, 18);
            try {
                byte[] plain = gcmDecrypt(key, iv, aad, Arrays.copyOfRange(raw, 30, 18 + len));
                f.valid = true;
                int start = 0;
                if (fromDevice && plain.length >= 4 && plain[0] == 0 && plain[1] == 0 && plain[2] == 0) {
                    f.retcode = ByteBuffer.wrap(plain, 0, 4).getInt();
                    start = 4;
                }
                f.payload = Arrays.copyOfRange(plain, start, plain.length);
            } catch (GeneralSecurityException e) {
                f.valid = false;
            }
        }
        return f;
    }
}
