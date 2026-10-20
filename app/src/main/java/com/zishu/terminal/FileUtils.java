package com.zishu.terminal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class FileUtils {
    private FileUtils() {}

    public static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.US, "%.2f GB", gb);
    }

    public static String formatDate(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(ms));
    }

    public static boolean copy(File src, File dst) {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            if (src.isDirectory()) {
                if (!dst.exists() && !dst.mkdirs()) return false;
                File[] kids = src.listFiles();
                if (kids == null) return true;
                for (int i = 0; i < kids.length; i++) {
                    if (!copy(kids[i], new File(dst, kids[i].getName()))) return false;
                }
                return true;
            }
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            close(in);
            close(out);
        }
    }

    public static boolean deleteRecursive(File f) {
        if (f == null) return false;
        try {
            if (f.isDirectory()) {
                File[] kids = f.listFiles();
                if (kids != null) {
                    for (int i = 0; i < kids.length; i++) deleteRecursive(kids[i]);
                }
            }
            return f.delete();
        } catch (Throwable t) {
            return false;
        }
    }

    public static String readText(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] data = new byte[(int) Math.min(f.length(), Integer.MAX_VALUE)];
            int off = 0;
            int n;
            while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) {
                off += n;
            }
            return new String(data, 0, off, "UTF-8");
        } finally {
            close(in);
        }
    }

    public static void writeText(File f, String text) throws IOException {
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(text.getBytes("UTF-8"));
            out.flush();
        } finally {
            close(out);
        }
    }

    private static void close(InputStream in) {
        if (in != null) try { in.close(); } catch (IOException ignored) {}
    }

    private static void close(OutputStream out) {
        if (out != null) try { out.close(); } catch (IOException ignored) {}
    }
}