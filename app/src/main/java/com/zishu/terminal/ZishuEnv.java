package com.zishu.terminal;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central environment model for Zishu Terminal v5.
 * Holds PREFIX / HOME / TMPDIR / PATH and exposes them as shell export lines.
 * Never escapes Android sandbox.
 */
public final class ZishuEnv {

    private final Context ctx;
    private final File filesDir;

    public ZishuEnv(Context c) {
        this.ctx = c.getApplicationContext();
        // Use /data/data/<pkg>/files (the prefix the packages were built for) instead of
        // /data/user/0/... so string comparisons in upstream scripts match.
        File canon = new File("/data/data/" + this.ctx.getPackageName() + "/files");
        this.filesDir = canon.isDirectory() ? canon : this.ctx.getFilesDir();
    }

    public File getFilesRoot() { return filesDir; }

    /** True only when the real bootstrap was installed AND verified (marker + bash present). */
    public boolean isBootstrapInstalled() {
        return new File(getPrefix(), ".zishu_bootstrap_ok").isFile()
                && new File(getBinDir(), "bash").exists();
    }

    public String shellPath() {
        return isBootstrapInstalled() ? new File(getBinDir(), "bash").getAbsolutePath() : "/system/bin/sh";
    }

    /** Environment for child processes (real userspace only; empty extras for the Android sh fallback). */
    public Map<String, String> processEnv() {
        Map<String, String> m = new LinkedHashMap<String, String>(asMap());
        if (isBootstrapInstalled()) {
            m.put("LANG", "en_US.UTF-8");
            m.put("TERM", "xterm-256color");
            m.put("COLORTERM", "truecolor");
            m.put("ANDROID_DATA", "/data");
            m.put("ANDROID_ROOT", "/system");
            File pre = new File(getLibDir(), "libtermux-exec-ld-preload.so");
            if (!pre.isFile()) pre = new File(getLibDir(), "libtermux-exec.so");
            if (pre.isFile()) m.put("LD_PRELOAD", pre.getAbsolutePath());
        }
        return m;
    }

    /** Point ShellProcess at the current shell + environment. */
    public void applyToShellProcess() {
        ShellProcess.configure(shellPath(), processEnv());
    }

    /** Run a command with the userspace env, return combined output (null on failure). Blocking. */
    public String runAndCapture(String[] cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().putAll(processEnv());
            pb.directory(getHome());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            InputStream in = p.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            p.waitFor();
            return bo.toString("UTF-8");
        } catch (Throwable t) {
            return "exec failed: " + t;
        }
    }

    public File getPrefix() { return new File(filesDir, "usr"); }
    public File getHome()   { return new File(filesDir, "home"); }
    public File getTmp()    { return new File(filesDir, "tmp"); }
    public File getBinDir() { return new File(getPrefix(), "bin"); }
    public File getLibDir() { return new File(getPrefix(), "lib"); }
    public File getEtcDir() { return new File(getPrefix(), "etc"); }
    public File getVarDir() { return new File(getPrefix(), "var"); }
    public File getShareDir(){ return new File(getPrefix(), "share"); }
    public File getProjectsDir() { return new File(getHome(), "projects"); }
    public File getPkgDbFile() { return new File(getVarDir(), "zpkg.db"); }

    public void ensureAll() {
        mkdirs(getPrefix());
        mkdirs(getHome());
        mkdirs(getTmp());
        mkdirs(getBinDir());
        mkdirs(getLibDir());
        mkdirs(getEtcDir());
        mkdirs(getVarDir());
        mkdirs(getShareDir());
        mkdirs(getProjectsDir());
    }

    public Map<String, String> asMap() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("PREFIX", getPrefix().getAbsolutePath());
        m.put("HOME",   getHome().getAbsolutePath());
        m.put("TMPDIR", getTmp().getAbsolutePath());
        // PATH includes Zishu bin + android system bin so real commands are found.
        m.put("PATH", getBinDir().getAbsolutePath() + ":/system/bin:/system/xbin");
        m.put("SHELL", shellPath());
        return m;
    }

    /**
     * Build a shell snippet exporting all vars; safe to prepend to each command.
     *
     * IMPORTANT: ends with a trailing newline. Without it, /system/bin/sh
     * buffers the line and never executes the exports until the user presses
     * Enter — which was the root cause of the empty $HOME bug.
     */
    public String exportSnippet() {
        StringBuilder sb = new StringBuilder();
        Map<String, String> m = asMap();
        for (Map.Entry<String, String> e : m.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (v == null) v = "";
            // Escape backslashes and double quotes so the shell sees exactly
            // the value Java intends.
            v = v.replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("export ").append(k).append("=\"")
              .append(v).append("\"; ");
        }
        sb.append('\n');
        return sb.toString();
    }

    private static void mkdirs(File f) {
        try { if (!f.exists()) f.mkdirs(); } catch (Throwable ignored) {}
    }
}
