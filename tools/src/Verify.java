import com.android.apksig.ApkVerifier;

import java.io.File;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;

/** Verifica la firma de un APK e imprime la huella del certificado. */
public final class Verify {
    public static void main(String[] a) throws Exception {
        ApkVerifier.Result r = new ApkVerifier.Builder(new File(a[0])).build().verify();
        System.out.println("verificado=" + r.isVerified() + " v2=" + r.isVerifiedUsingV2Scheme() + " v3=" + r.isVerifiedUsingV3Scheme());
        for (X509Certificate c : r.getSignerCertificates()) {
            StringBuilder b = new StringBuilder();
            for (byte x : MessageDigest.getInstance("SHA-256").digest(c.getEncoded())) b.append(String.format("%02X:", x));
            System.out.println("certificado SHA-256 " + b.substring(0, b.length() - 1));
        }
        for (ApkVerifier.IssueWithParams e : r.getErrors()) System.out.println("ERROR " + e);
        System.exit(r.isVerified() ? 0 : 1);
    }
}
