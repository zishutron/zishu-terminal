package com.zishu.terminal;

import android.content.Context;
import android.os.Build;
import android.os.StatFs;
import android.system.ErrnoException;
import android.system.Os;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads, verifies and installs the Zishu userspace bootstrap (bash, coreutils,
 * apt, curl ...) built by the "Build Bootstrap" workflow with prefix
 * /data/data/com.zishu.terminal/files/usr.
 *
 * Security: the zip is only extracted if its SHA-256 equals the value pinned in
 * assets/bootstrap.properties. Entries that escape the staging dir are rejected.
 * Nothing is reported as installed until a real command has been executed.
 */
public class BootstrapManager {

    public interface Log { void line(String s); }

    private static final long MIN_FREE_BYTES = 600L * 1024 * 1024;

    private final Context ctx;
    private final ZishuEnv env;

    public BootstrapManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.env = new ZishuEnv(this.ctx);
    }

    /** "aarch64", "arm" or null when unsupported. */
    public static String detectArch() {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis == null) return null;
        for (int i = 0; i < abis.length; i++) {
            if ("arm64-v8a".equals(abis[i])) return "aarch64";
            if ("armeabi-v7a".equals(abis[i])) return "arm";
        }
        return null;
    }

    public boolean isInstalled() { return env.isBootstrapInstalled(); }

    // ------------------------------------------------------------------

    /** Blocking. Call from a background thread. Returns true only if verified working. */
    public boolean install(Log log) {
        File staging = new File(env.getFilesRoot(), "usr.staging");
        try {
            String arch = detectArch();
            if (arch == null) {
                log.line("Unsupported ABI: " + java.util.Arrays.toString(Build.SUPPORTED_ABIS)
                        + " (supported: arm64-v8a, armeabi-v7a)");
                return false;
            }
            log.line("Device architecture: " + arch);

            Properties cfg = new Properties();
            InputStream in = ctx.getAssets().open("bootstrap.properties");
            try { cfg.load(in); } finally { in.close(); }
            String repo = cfg.getProperty("repo", "").trim();
            String tag = cfg.getProperty("tag", "").trim();
            String sha = cfg.getProperty(arch + ".sha256", "").trim().toLowerCase();
            if (repo.length() == 0 || tag.length() == 0 || sha.length() != 64) {
                log.line("Bootstrap is not configured: set tag and " + arch
                        + ".sha256 in assets/bootstrap.properties after the Build Bootstrap workflow succeeds.");
                return false;
            }

            File root = env.getFilesRoot();
            StatFs st = new StatFs(root.getAbsolutePath());
            long free = (long) st.getAvailableBlocks() * (long) st.getBlockSize();
            if (free < MIN_FREE_BYTES) {
                log.line("Not enough free storage: need ~600 MB, have " + (free / 1048576) + " MB");
                return false;
            }

            String url = "https://github.com/" + repo + "/releases/download/" + tag
                    + "/bootstrap-" + arch + ".zip";
            File zip = new File(ctx.getCacheDir(), "bootstrap-" + arch + ".zip");
            if (!(zip.isFile() && sha.equals(sha256(zip)))) {
                log.line("Downloading " + url);
                download(url, zip, log);
                String got = sha256(zip);
                if (!sha.equals(got)) {
                    zip.delete();
                    log.line("SHA-256 MISMATCH. expected " + sha + " got " + got + ". Refusing to install.");
                    return false;
                }
            }
            log.line("SHA-256 verified.");

            deleteRecursive(staging);
            if (!staging.mkdirs()) { log.line("Cannot create " + staging); return false; }
            extract(zip, staging, log);
            patchOldPrefix(staging, log);
            configureMirror(staging, repo, log);

            // Swap in: keep the previous prefix (old zpkg data) as usr.pre-bootstrap.
            File usr = env.getPrefix();
            File old = new File(root, "usr.pre-bootstrap");
            if (usr.exists()) {
                deleteRecursive(old);
                if (!usr.renameTo(old)) { log.line("Cannot move old prefix"); return false; }
            }
            if (!staging.renameTo(usr)) { log.line("Cannot activate new prefix"); return false; }
            env.ensureAll();
            env.applyToShellProcess();

            runSecondStage(log);

            String out = env.runAndCapture(new String[]{ new File(env.getBinDir(), "bash").getPath(), "--version" });
            if (out == null || out.indexOf("GNU bash") < 0) {
                log.line("Health check FAILED: bash --version did not run. Output: " + out);
                return false;
            }
            new File(usr, ".zishu_bootstrap_ok").createNewFile();
            env.applyToShellProcess();   // now bash + LD_PRELOAD for new sessions
            log.line("Health check OK: " + out.split("\n")[0]);
            log.line("Bootstrap installed. Now fully close and reopen the app (or Menu -> Restart Shell) to get real bash.");
            return true;
        } catch (Throwable t) {
            log.line("Bootstrap failed: " + t);
            return false;
        } finally {
            if (staging.exists()) deleteRecursive(staging);
        }
    }

    // ------------------------------------------------------------------

    private void download(String url, File dest, Log log) throws IOException {
        File part = new File(dest.getPath() + ".part");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code + " for " + url);
        long total = c.getContentLength();
        InputStream in = c.getInputStream();
        OutputStream out = new FileOutputStream(part);
        try {
            byte[] buf = new byte[65536];
            long done = 0, lastLog = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (done - lastLog >= 5L * 1048576) {
                    lastLog = done;
                    log.line("  " + (done / 1048576) + (total > 0 ? " / " + (total / 1048576) : "") + " MB");
                }
            }
        } finally {
            try { out.close(); } catch (IOException ignored) {}
            try { in.close(); } catch (IOException ignored) {}
            c.disconnect();
        }
        if (dest.exists()) dest.delete();
        if (!part.renameTo(dest)) throw new IOException("Cannot move download into place");
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        } finally { in.close(); }
        byte[] d = md.digest();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length; i++) sb.append(String.format("%02x", d[i] & 0xff));
        return sb.toString();
    }

    private void extract(File zip, File dest, Log log) throws Exception {
        String base = dest.getCanonicalPath() + File.separator;
        List<String> symlinkLines = new ArrayList<String>();
        int files = 0;
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zip));
        try {
            ZipEntry e;
            byte[] buf = new byte[65536];
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (name.equals("SYMLINKS.txt")) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    int n;
                    while ((n = zis.read(buf)) > 0) bo.write(buf, 0, n);
                    String[] ls = bo.toString("UTF-8").split("\n");
                    for (int i = 0; i < ls.length; i++) if (ls[i].length() > 0) symlinkLines.add(ls[i]);
                    continue;
                }
                File target = new File(dest, name);
                String canon = target.getCanonicalPath();
                if (!(canon + File.separator).startsWith(base) && !canon.equals(base.substring(0, base.length() - 1))) {
                    throw new IOException("Unsafe path in archive: " + name);
                }
                if (e.isDirectory()) {
                    target.mkdirs();
                    chmod(target, 0700);
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null) parent.mkdirs();
                OutputStream out = new FileOutputStream(target);
                try {
                    int n;
                    while ((n = zis.read(buf)) > 0) out.write(buf, 0, n);
                } finally { out.close(); }
                boolean exec = name.startsWith("bin/") || name.startsWith("libexec")
                        || name.startsWith("lib/apt/apt-helper") || name.startsWith("lib/apt/methods")
                        || name.endsWith("-second-stage.sh");
                chmod(target, exec ? 0700 : 0600);
                files++;
            }
        } finally { zis.close(); }

        // SYMLINKS.txt lines: "<target>←<linkpath>"
        int links = 0;
        for (int i = 0; i < symlinkLines.size(); i++) {
            String ln = symlinkLines.get(i);
            int idx = ln.indexOf('←');
            if (idx <= 0) throw new IOException("Bad SYMLINKS.txt line: " + ln);
            String tgt = ln.substring(0, idx);
            String lnk = ln.substring(idx + 1);
            File linkFile = new File(dest, lnk);
            String canonParent = linkFile.getParentFile().getCanonicalPath() + File.separator;
            if (!canonParent.startsWith(base)) throw new IOException("Unsafe symlink path: " + lnk);
            linkFile.getParentFile().mkdirs();
            try { Os.symlink(tgt, linkFile.getPath()); links++; }
            catch (ErrnoException ex) { throw new IOException("symlink " + lnk + ": " + ex.getMessage()); }
        }
        log.line("Extracted " + files + " files, " + links + " symlinks.");
    }

    // pkg (termux-tools) otherwise tests official Termux mirrors and rewrites sources.list to one of
    // them. Lock it to the Zishu repository only (single mirror file selected via chosen_mirrors).
    private void configureMirror(File root, String repo, Log log) throws Exception {
        String[] pr = repo.split("/");
        if (pr.length != 2) throw new IOException("bad repo in bootstrap.properties: " + repo);
        String url = "https://" + pr[0].toLowerCase() + ".github.io/" + pr[1] + "/apt";
        File mirrors = new File(root, "etc/termux/mirrors");
        deleteRecursive(mirrors);
        mirrors.mkdirs();
        writeText(new File(mirrors, "zishu"),
                "# Zishu Terminal apt repository\nWEIGHT=1\nMAIN=\"" + url + "\"\n");
        File chosen = new File(root, "etc/termux/chosen_mirrors");
        deleteRecursive(chosen);
        Os.symlink("mirrors/zishu", chosen.getPath());
        File apt = new File(root, "etc/apt");
        apt.mkdirs();
        writeText(new File(apt, "sources.list"), "deb " + url + " stable main\n");
        log.line("apt repository: " + url);
    }

    private static void writeText(File f, String text) throws IOException {
        OutputStream o = new FileOutputStream(f);
        try { o.write(text.getBytes("UTF-8")); } finally { o.close(); }
    }

    // Some upstream scripts (pkg, login, termux-reset, ...) keep the old com.termux path.
    // Rewrite it in TEXT files only (ELF/binary files are never touched).
    private static final String OLD_PREFIX = "/data/data/com.termux";
    private static final String NEW_PREFIX = "/data/data/com.zishu.terminal";

    private void patchOldPrefix(File dir, Log log) {
        int[] count = new int[1];
        patchWalk(dir, count);
        log.line("Rewrote old prefix in " + count[0] + " text files.");
    }

    private static void patchWalk(File f, int[] count) {
        if (isLink(f)) return;
        if (f.isDirectory()) {
            File[] cs = f.listFiles();
            if (cs != null) for (int i = 0; i < cs.length; i++) patchWalk(cs[i], count);
            return;
        }
        if (!f.isFile() || f.length() == 0 || f.length() > 2L * 1024 * 1024) return;
        try {
            byte[] data = new byte[(int) f.length()];
            InputStream in = new FileInputStream(f);
            try {
                int off = 0, n;
                while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
            } finally { in.close(); }
            if (data.length > 4 && data[0] == 0x7f && data[1] == 'E' && data[2] == 'L' && data[3] == 'F') return;
            int lim = Math.min(data.length, 8000);
            for (int i = 0; i < lim; i++) if (data[i] == 0) return;
            String s = new String(data, "ISO-8859-1");   // byte-exact round trip
            if (s.indexOf(OLD_PREFIX) < 0) return;
            byte[] out = s.replace(OLD_PREFIX, NEW_PREFIX).getBytes("ISO-8859-1");
            OutputStream fo = new FileOutputStream(f);    // keeps existing permissions
            try { fo.write(out); } finally { fo.close(); }
            count[0]++;
        } catch (Throwable ignored) {}
    }

    private void runSecondStage(Log log) {
        File ss = findFile(new File(env.getPrefix(), "etc"), "termux-bootstrap-second-stage.sh");
        if (ss == null) { log.line("(no second-stage script found)"); return; }
        log.line("Running second stage ...");
        String out = env.runAndCapture(new String[]{ new File(env.getBinDir(), "bash").getPath(), ss.getPath() });
        if (out != null && out.trim().length() > 0) log.line(out.trim());
    }

    private static File findFile(File dir, String name) {
        File[] fs = dir.listFiles();
        if (fs == null) return null;
        for (int i = 0; i < fs.length; i++) {
            if (fs[i].isDirectory() && !isLink(fs[i])) {
                File r = findFile(fs[i], name);
                if (r != null) return r;
            } else if (fs[i].getName().equals(name)) return fs[i];
        }
        return null;
    }

    private static boolean isLink(File f) {
        try { return !f.getCanonicalPath().equals(new File(f.getParentFile().getCanonicalFile(), f.getName()).getPath()); }
        catch (IOException e) { return true; }
    }

    private static void chmod(File f, int mode) {
        try { Os.chmod(f.getPath(), mode); } catch (ErrnoException ignored) {}
    }

    static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        try {
            if (f.isDirectory() && !isLink(f)) {
                File[] cs = f.listFiles();
                if (cs != null) for (int i = 0; i < cs.length; i++) deleteRecursive(cs[i]);
            }
        } catch (Throwable ignored) {}
        f.delete();
    }
}
