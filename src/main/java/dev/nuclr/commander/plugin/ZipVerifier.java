package dev.nuclr.commander.plugin;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class ZipVerifier {

    public static boolean verify(
            File zip,
            File signatureFile,
            byte[] certBytes) throws Exception {

        byte[] zipBytes = Files.readAllBytes(zip.toPath());
        byte[] sigBytes = Files.readAllBytes(signatureFile.toPath());

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(certBytes));
        PublicKey pubKey = cert.getPublicKey();

        Signature sig = Signature.getInstance(cert.getSigAlgName());
        sig.initVerify(pubKey);
        sig.update(zipBytes);

        return sig.verify(sigBytes);
    }
}