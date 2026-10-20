package com.zishu.terminal;

import android.content.Context;

import java.io.File;
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
        this.filesDir = this.ctx.getFilesDir();
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
        m.put("SHELL", "/system/bin/sh");
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
