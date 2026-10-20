package com.zishu.terminal;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Zishu Protect - real file encryption for Zishu Terminal.
 *
 * Uses AES-256-GCM with PBKDF2-derived keys. Every file has a unique
 * random salt and IV. Integrity is protected by GCM authentication tag.
 *
 * Encrypted file format (.zenc):
 *   [0..3]   Magic: "ZENC"
 *   [4]      Version: 1
 *   [5]      KDF iterations (as unsigned byte, log-encoded)
 *   [6..21]  Salt (16 bytes)
 *   [22..33] IV (12 bytes)
 *   [34..35] Original filename length (big-endian uint16)
 *   [36..N]  Original filename (UTF-8)
 *   [N+1..]  Ciphertext + 16-byte GCM tag
 */
public final class ZishuProtect {

    public static final String EXT = ".zenc";
    private static final byte[] MAGIC = new byte[]{'Z', 'E', 'N', 'C'};
    private static final int VERSION = 1;
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BITS = 256;

    /** Default iterations. 100k is a good balance on mobile devices. */
    private static final int ITERATIONS = 100000;

    /** Chunk size for streaming (256 KB). */
    private static final int CHUNK = 256 * 1024;

    public interface Logger {
        void info(String s);
        void error(String s);
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    public static boolean encryptFile(File input, File output, String password, Logger log) {
        if (!checkInput(input, log)) return false;
        if (password == null || password.length() < 1) {
            log.error("Password cannot be empty.");
            return false;
        }
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            byte[] salt = randomBytes(SALT_LEN);
            byte[] iv = randomBytes(IV_LEN);

            SecretKey key = deriveKey(password, salt, ITERATIONS);

            // Write header
            out = new FileOutputStream(output);
            out.write(MAGIC);
            out.write(VERSION);
            out.write(encodeIterations(ITERATIONS));
            out.write(salt);
            out.write(iv);

            byte[] nameBytes = input.getName().getBytes("UTF-8");
            int nameLen = Math.min(nameBytes.length, 65535);
            out.write((nameLen >> 8) & 0xFF);
            out.write(nameLen & 0xFF);
            out.write(nameBytes, 0, nameLen);
            out.flush();

            // Encrypt body
            Cipher cipher = initCipher(Cipher.ENCRYPT_MODE, key, iv);
            in = new FileInputStream(input);

            CipherOutputStream cos = new CipherOutputStream(out, cipher);
            byte[] buf = new byte[CHUNK];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                cos.write(buf, 0, n);
                total += n;
            }
            cos.flush();
            cos.close();  // This writes the GCM tag

            log.info("Encrypted: " + input.getName()
                    + " → " + output.getName()
                    + " (" + total + " bytes)");
            return true;
        } catch (Throwable t) {
            log.error("Encryption failed: " + t.getMessage());
            try { if (output != null) output.delete(); } catch (Throwable ignored) {}
            return false;
        } finally {
            close(in);
            close(out);
        }
    }

    public static boolean decryptFile(File input, File output, String password, Logger log) {
        if (!checkInput(input, log)) return false;
        if (password == null || password.length() < 1) {
            log.error("Password cannot be empty.");
            return false;
        }
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(input);

            // Verify magic
            byte[] magic = new byte[4];
            if (in.read(magic) != 4 || !bytesEqual(magic, MAGIC)) {
                log.error("Not a Zishu encrypted file.");
                return false;
            }
            int version = in.read();
            if (version != VERSION) {
                log.error("Unsupported version: " + version);
                return false;
            }
            int iterByte = in.read();
            int iterations = decodeIterations(iterByte);

            byte[] salt = new byte[SALT_LEN];
            if (in.read(salt) != SALT_LEN) {
                log.error("Truncated header.");
                return false;
            }
            byte[] iv = new byte[IV_LEN];
            if (in.read(iv) != IV_LEN) {
                log.error("Truncated header.");
                return false;
            }
            int nameLen = (in.read() << 8) | in.read();
            byte[] nameBytes = new byte[nameLen];
            int readName = 0;
            while (readName < nameLen) {
                int r = in.read(nameBytes, readName, nameLen - readName);
                if (r < 0) break;
                readName += r;
            }

            SecretKey key = deriveKey(password, salt, iterations);
            Cipher cipher = initCipher(Cipher.DECRYPT_MODE, key, iv);

            // Determine output filename
            if (output == null) {
                String originalName = new String(nameBytes, 0, readName, "UTF-8");
                File parent = input.getParentFile();
                output = new File(parent, originalName);
            }

            out = new FileOutputStream(output);
            CipherInputStream cis = new CipherInputStream(in, cipher);
            byte[] buf = new byte[CHUNK];
            int n;
            long total = 0;
            while ((n = cis.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
            }
            out.flush();
            cis.close();

            log.info("Decrypted: " + input.getName()
                    + " → " + output.getName()
                    + " (" + total + " bytes)");
            return true;
        } catch (javax.crypto.AEADBadTagException tagErr) {
            log.error("Wrong password or corrupted file.");
            try { if (output != null) output.delete(); } catch (Throwable ignored) {}
            return false;
        } catch (Throwable t) {
            log.error("Decryption failed: " + t.getMessage());
            try { if (output != null) output.delete(); } catch (Throwable ignored) {}
            return false;
        } finally {
            close(in);
            close(out);
        }
    }

    /** Reads header info without decrypting. */
    public static FileInfo readInfo(File input) {
        if (input == null || !input.isFile()) return null;
        FileInputStream in = null;
        try {
            in = new FileInputStream(input);
            byte[] magic = new byte[4];
            if (in.read(magic) != 4 || !bytesEqual(magic, MAGIC)) return null;
            int version = in.read();
            int iterByte = in.read();
            int iterations = decodeIterations(iterByte);
            byte[] salt = new byte[SALT_LEN];
            if (in.read(salt) != SALT_LEN) return null;
            byte[] iv = new byte[IV_LEN];
            if (in.read(iv) != IV_LEN) return null;
            int nameLen = (in.read() << 8) | in.read();
            byte[] nameBytes = new byte[nameLen];
            int r = 0;
            while (r < nameLen) {
                int x = in.read(nameBytes, r, nameLen - r);
                if (x < 0) break;
                r += x;
            }
            FileInfo fi = new FileInfo();
            fi.version = version;
            fi.iterations = iterations;
            fi.originalName = new String(nameBytes, 0, r, "UTF-8");
            fi.encryptedSize = input.length();
            return fi;
        } catch (Throwable t) {
            return null;
        } finally {
            close(in);
        }
    }

    /** Validates a password by attempting to decrypt just the tag. */
    public static boolean verifyPassword(File input, String password) {
        if (input == null || !input.isFile()) return false;
        FileInputStream in = null;
        FileOutputStream devNull = null;
        try {
            in = new FileInputStream(input);
            byte[] magic = new byte[4];
            if (in.read(magic) != 4 || !bytesEqual(magic, MAGIC)) return false;
            int version = in.read();
            if (version != VERSION) return false;
            int iterByte = in.read();
            int iterations = decodeIterations(iterByte);
            byte[] salt = new byte[SALT_LEN];
            if (in.read(salt) != SALT_LEN) return false;
            byte[] iv = new byte[IV_LEN];
            if (in.read(iv) != IV_LEN) return false;
            int nameLen = (in.read() << 8) | in.read();
            byte[] nameBytes = new byte[nameLen];
            int r = 0;
            while (r < nameLen) {
                int x = in.read(nameBytes, r, nameLen - r);
                if (x < 0) break;
                r += x;
            }

            SecretKey key = deriveKey(password, salt, iterations);
            Cipher cipher = initCipher(Cipher.DECRYPT_MODE, key, iv);

            // Read remaining bytes through CipherInputStream; if the tag
            // fails, it throws AEADBadTagException.
            CipherInputStream cis = new CipherInputStream(in, cipher);
            byte[] buf = new byte[4096];
            while (cis.read(buf) > 0) {
                // discard
            }
            cis.close();
            return true;
        } catch (javax.crypto.AEADBadTagException t) {
            return false;
        } catch (Throwable t) {
            return false;
        } finally {
            close(in);
            close(devNull);
        }
    }

    // ---------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------

    private static Cipher initCipher(int mode, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(TAG_BITS, iv);
        cipher.init(mode, key, spec);
        return cipher;
    }

    private static SecretKey deriveKey(String password, byte[] salt, int iterations) throws Exception {
        // API 26+ supports PBKDF2WithHmacSHA256. Fall back to SHA1 on
        // older devices. Both are secure; SHA256 is preferred.
        String algo;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            algo = "PBKDF2WithHmacSHA256";
        } else {
            algo = "PBKDF2WithHmacSHA1";
        }
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(algo);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static byte[] randomBytes(int n) {
        byte[] out = new byte[n];
        new SecureRandom().nextBytes(out);
        return out;
    }

    private static boolean bytesEqual(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= (a[i] ^ b[i]);
        return diff == 0;
    }

    /** Encode iterations as a compact byte (log2/16). */
    private static int encodeIterations(int iters) {
        // Map 100k iterations to byte value 100
        // We store approximate; exact restored from byte * 1000
        return Math.min(255, iters / 1000);
    }

    private static int decodeIterations(int b) {
        if (b <= 0) return 100000;
        return b * 1000;
    }

    private static void close(InputStream in) {
        if (in != null) try { in.close(); } catch (Throwable ignored) {}
    }

    private static void close(OutputStream out) {
        if (out != null) try { out.close(); } catch (Throwable ignored) {}
    }

    private static boolean checkInput(File f, Logger log) {
        if (f == null) { log.error("No file specified."); return false; }
        if (!f.exists()) { log.error("File not found: " + f.getAbsolutePath()); return false; }
        if (!f.isFile()) { log.error("Not a file: " + f.getAbsolutePath()); return false; }
        if (!f.canRead()) { log.error("Cannot read: " + f.getAbsolutePath()); return false; }
        return true;
    }

    // ---------------------------------------------------------------
    // Data class
    // ---------------------------------------------------------------

    public static class FileInfo {
        public int version;
        public int iterations;
        public String originalName;
        public long encryptedSize;
    }

    private ZishuProtect() {}
}