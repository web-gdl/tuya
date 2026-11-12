"""Vectores de referencia escritos de forma independiente (Python + cryptography)
a partir de la especificación del protocolo local de Tuya. Las pruebas Java deben
producir exactamente los mismos bytes."""
import struct, zlib, hmac, hashlib, base64, json
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

KEY = b'0123456789abcdef'
UDP = hashlib.md5(b'yGAdlopoPVldABfn').digest()

def pad(b):
    n = 16 - len(b) % 16
    return b + bytes([n]) * n

def ecb(key, data, padding=True):
    e = Cipher(algorithms.AES(key), modes.ECB()).encryptor()
    return e.update(pad(data) if padding else data) + e.finalize()

def f55(seq, cmd, payload, hkey=None, ret=None):
    body = (struct.pack('>I', ret) if ret is not None else b'') + payload
    tail = 36 if hkey else 8
    d = struct.pack('>4I', 0x55AA, seq, cmd, len(body) + tail) + body
    d += hmac.new(hkey, d, hashlib.sha256).digest() if hkey else struct.pack('>I', zlib.crc32(d) & 0xFFFFFFFF)
    return d + struct.pack('>I', 0xAA55)

def f66(seq, cmd, payload, key, iv, ret=None):
    body = (struct.pack('>I', ret) if ret is not None else b'') + payload
    head = struct.pack('>IHIII', 0x6699, 0, seq, cmd, 12 + len(body) + 16)
    return head + iv + AESGCM(key).encrypt(iv, body, head[4:]) + struct.pack('>I', 0x9966)

V = {}
js = b'{"devId":"abc","uid":"abc","t":"1700000000","dps":{"1":true}}'
V['crc_plain'] = f55(7, 10, b'{"gwId":"abc"}')
V['hmac_frame'] = f55(3, 13, ecb(KEY, b'hola'), KEY)
V['dev_crc_ret'] = f55(9, 10, ecb(KEY, b'{"dps":{"1":true}}'), None, 0)
V['gcm_frame'] = f66(5, 16, b'{}', KEY, b'ABCDEFGHIJKL')
V['gcm_dev'] = f66(6, 8, b'3.5' + bytes(12) + b'{"data":{"dps":{"2":false}}}', KEY, b'LKJIHGFEDCBA', 0)
# cliente 3.3: CONTROL lleva cabecera en claro + cifrado; DP_QUERY solo cifrado
V['c33_control'] = f55(2, 7, b'3.3' + bytes(12) + ecb(KEY, js))
V['c33_query'] = f55(1, 10, ecb(KEY, js))
# cliente 3.1: firma md5 + base64
b64 = base64.b64encode(ecb(KEY, js))
sig = hashlib.md5(b'data=' + b64 + b'||lpv=3.1||' + KEY).hexdigest()[8:24].encode()
V['c31_control'] = f55(4, 7, b'3.1' + sig + b64)
V['c31_query'] = f55(4, 10, js)
# cliente 3.4: cabecera dentro del cifrado, HMAC
V['c34_control'] = f55(8, 13, ecb(KEY, b'3.4' + bytes(12) + js), KEY)
V['c34_query'] = f55(8, 16, ecb(KEY, b'{}'), KEY)
# claves de sesión
ln, rn = b'0123456789abcdef', bytes(range(100, 116))
x = bytes(a ^ b for a, b in zip(ln, rn))
V['sess34'] = ecb(KEY, x, False)
V['sess35'] = AESGCM(KEY).encrypt(ln[:12], x, None)[:16]
V['neg_remote'] = rn
V['neg_hmac_local'] = hmac.new(KEY, ln, hashlib.sha256).digest()
# anuncios UDP
ann = json.dumps({"ip": "192.168.1.50", "gwId": "bf1234567890abcdefghij", "active": 2, "ability": 0, "mode": 0,
                  "encrypt": True, "productKey": "keyabc", "version": "3.3"}, separators=(',', ':')).encode()
V['udp33'] = f55(0, 0x13, ecb(UDP, ann), None, 0)
V['udp31'] = f55(0, 0, ann.replace(b'"3.3"', b'"3.1"'), None, 0)
V['udp35'] = f66(0, 0x13, ann.replace(b'"3.3"', b'"3.5"'), UDP, b'000000000000', 0)
with open('test/vectors.txt', 'w') as f:
    for k, v in V.items():
        f.write('%s=%s\n' % (k, v.hex()))
    msg = 'cid123' + 'tok456' + '1700000000000' + 'GET\n' + hashlib.sha256(b'').hexdigest() + '\n\n' + '/v1.0/iot-01/associated-users/devices?size=50'
    f.write('sign=%s\n' % hmac.new(b'secreto', msg.encode(), hashlib.sha256).hexdigest().upper())
print(len(V) + 1, 'vectores')
