package Util;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

public final class CalculadorSHA256 {
    private CalculadorSHA256() {}
    public static String calcular(Path arquivo) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new BufferedInputStream(Files.newInputStream(arquivo))) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Operação interrompida.");
                    digest.update(buffer, 0, n);
                }
            }
            return HexFormat.of().withUpperCase().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
