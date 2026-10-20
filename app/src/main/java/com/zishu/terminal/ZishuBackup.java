package com.zishu.terminal;

import android.content.Context;

import java.io.File;

/**
 * Local backup of Zishu preferences, history and package DB.
 * Never touches Android system data.
 */
public class ZishuBackup {

    public interface Logger { void line(String s); }

    private final Context ctx;
    private final Logger logger;

    public ZishuBackup(Context ctx, Logger logger) {
        this.ctx = ctx.getApplicationContext();
        this.logger = logger;
    }

    public File backupRoot() {
        File f = new File(ctx.getFilesDir(), "backup");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public void backup() {
        File dst = new File(backupRoot(), "backup_" + System.currentTimeMillis());
        dst.mkdirs();
        boolean ok = true;
        ok &= copyPrefs(dst);
        ok &= copyHistory(dst);
        ok &= copyPkgDb(dst);
        if (ok) log("Backup created: " + dst.getAbsolutePath());
        else    log("Backup completed with warnings: " + dst.getAbsolutePath());
    }

    public void restore() {
        File root = backupRoot();
        File[] kids = root.listFiles();
        if (kids == null || kids.length == 0) { log("No backups found."); return; }
        File latest = null;
        for (int i = 0; i < kids.length; i++) {
            if (kids[i].isDirectory()) {
                if (latest == null || kids[i].lastModified() > latest.lastModified())
                    latest = kids[i];
            }
        }
        if (latest == null) { log("No backups found."); return; }

        // Restore prefs (merge)
        File prefsXml = new File(latest, "shared_prefs.xml");
        if (prefsXml.isFile()) {
            log("Preferences backup found. Restore is limited to history + DB in v3.5.");
        }

        File histFile = new File(latest, "history.txt");
        if (histFile.isFile()) {
            try {
                String blob = FileUtils.readText(histFile);
                Prefs.get(ctx).edit().putString("cmd_history_v1", blob).apply();
                log("History restored.");
            } catch (Throwable t) {
                log("History restore failed: " + t.getMessage());
            }
        }

        File pkgDb = new File(latest, "zpkg.db");
        if (pkgDb.isFile()) {
            try {
                ZishuEnv env = new ZishuEnv(ctx);
                FileUtils.copy(pkgDb, env.getPkgDbFile());
                log("Package DB restored.");
            } catch (Throwable t) {
                log("Package DB restore failed: " + t.getMessage());
            }
        }
        log("Restore complete from: " + latest.getAbsolutePath());
    }

    public void reset() {
        Prefs.reset(ctx);
        log("Preferences reset.");
    }

    private boolean copyPrefs(File dst) {
        try {
            File sp = new File(ctx.getFilesDir().getParentFile(),
                    "shared_prefs/zishu_prefs.xml");
            if (sp.isFile()) return FileUtils.copy(sp, new File(dst, "shared_prefs.xml"));
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean copyHistory(File dst) {
        try {
            String blob = Prefs.get(ctx).getString("cmd_history_v1", "");
            FileUtils.writeText(new File(dst, "history.txt"), blob);
            return true;
        } catch (Throwable t) { return false; }
    }

    private boolean copyPkgDb(File dst) {
        try {
            ZishuEnv env = new ZishuEnv(ctx);
            File src = env.getPkgDbFile();
            if (!src.exists()) return true;
            return FileUtils.copy(src, new File(dst, "zpkg.db"));
        } catch (Throwable t) { return false; }
    }

    private void log(String s) { if (logger != null) logger.line(s); }
}