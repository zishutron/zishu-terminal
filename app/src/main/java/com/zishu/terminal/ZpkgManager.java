package com.zishu.terminal;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zishu Package Manager v6.0.0
 *
 * Real local package manager. Packages are user-provided directories
 * containing a MANIFEST file. Nothing is downloaded automatically and
 * nothing is executed automatically.
 *
 * Remote repository support is planned for v5.5.
 */
public class ZpkgManager {

    public interface Logger {
        void line(String s);
    }

    private final Context ctx;
    private final ZishuEnv env;
    private final PackageDatabase db;
    private final Logger logger;

    public ZpkgManager(Context ctx, Logger logger) {
        this.ctx = ctx.getApplicationContext();
        this.env = new ZishuEnv(this.ctx);
        this.db = new PackageDatabase(this.ctx);
        this.logger = logger;
        this.env.ensureAll();
    }

    public PackageDatabase db() { return db; }

    // ------------------------------------------------------------
    // HELP
    // ------------------------------------------------------------

    public void help() {
        log("Zishu Package Manager (zpkg) v6.0.0");
        log("  zpkg help                Show this help");
        log("  zpkg list                List all known packages");
        log("  zpkg installed           List installed packages");
        log("  zpkg info <name>         Show package details");
        log("  zpkg search <term>       Search local package DB");
        log("  zpkg install <dir>       Install a .zpkg directory");
        log("  zpkg remove <name>       Remove an installed package");
        log("  zpkg update              Refresh local package index");
        log("  zpkg upgrade             Check for upgrades");
        log("  zpkg clean               Clean package cache");
        log("  zpkg doctor              Check package database");
        log("  zpkg repo list           List repositories");
        log("  zpkg repo add <n> <url>  Add HTTPS repository");
        log("  zpkg repo remove <name>  Remove repository");
        log("  zpkg repo enable <name>  Enable repository");
        log("  zpkg repo disable <name> Disable repository");
        log("");
        log("─── Current limitations ───");
        log("zpkg v6.0.0 manages local .zpkg packages only.");
        log("zpkg handles local .zpkg packages only. For real packages use: pkg install <name>");
        log("Local .zpkg packages are fully supported.");
        log("Remote repositories are planned for a future release.");
    }

    // ------------------------------------------------------------
    // LIST / INFO / SEARCH
    // ------------------------------------------------------------

    public void list() {
        List<PackageInfo> all = db.all();
        if (all.isEmpty()) { log("(no packages installed)"); return; }
        for (int i = 0; i < all.size(); i++) {
            PackageInfo p = all.get(i);
            log(p.name + " " + p.version + "  [" + p.architecture + "]");
        }
    }

    public void installed() { list(); }

    public void info(String name) {
        PackageInfo p = db.byName(name);
        if (p == null) { log("Package not found: " + name); return; }
        log("Name:         " + p.name);
        log("Version:      " + p.version);
        log("Description:  " + p.description);
        log("Architecture: " + p.architecture);
        log("Source:       " + p.source);
        log("Integrity:    " + p.integrity);
        log("Installed at: " + p.installedAt);
        if (p.dependencies != null && p.dependencies.length > 0) {
            log("Dependencies:");
            for (int i = 0; i < p.dependencies.length; i++) {
                log("  " + p.dependencies[i]);
            }
        }
        if (p.files.length > 0) {
            log("Files:");
            for (int i = 0; i < p.files.length; i++) log("  " + p.files[i]);
        }
    }

    public void search(String term) {
        if (term == null || term.length() == 0) {
            log("Usage: zpkg search <term>");
            return;
        }
        String t = term.toLowerCase(Locale.US);
        List<PackageInfo> all = db.all();
        int found = 0;
        for (int i = 0; i < all.size(); i++) {
            PackageInfo p = all.get(i);
            if ((p.name != null && p.name.toLowerCase(Locale.US).contains(t))
                    || (p.description != null && p.description.toLowerCase(Locale.US).contains(t))) {
                log(p.name + " " + p.version + "  " + p.description);
                found++;
            }
        }
        if (found == 0) log("No local packages match: " + term);
    }

    // ------------------------------------------------------------
    // INSTALL
    // ------------------------------------------------------------

    /** Install a local .zpkg directory. */
    public void install(String path) {
        if (path == null || path.length() == 0) {
            log("Usage: zpkg install <dir>");
            return;
        }
        File pkgDir = new File(path);
        if (!pkgDir.isDirectory()) {
            log("Not a directory: " + path);
            return;
        }
        File manifest = new File(pkgDir, "MANIFEST");
        if (!manifest.isFile()) {
            log("MANIFEST not found in " + path);
            return;
        }

        PackageInfo info = new PackageInfo();
        info.source = "local:" + pkgDir.getAbsolutePath();
        info.installedAt = System.currentTimeMillis();
        info.architecture = "any";

        // --- Parse MANIFEST ---
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(manifest));
            String line;
            while ((line = r.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq).trim();
                String v = line.substring(eq + 1).trim();
                if (k.equals("name")) info.name = v;
                else if (k.equals("version")) info.version = v;
                else if (k.equals("description")) info.description = v;
                else if (k.equals("architecture")) info.architecture = v;
                else if (k.equals("deps") || k.equals("dependencies")) {
                    if (v.length() > 0) info.dependencies = v.split("\\|");
                }
            }
        } catch (Throwable t) {
            log("Failed to read MANIFEST: " + t.getMessage());
            return;
        } finally {
            if (r != null) try { r.close(); } catch (Throwable ignored) {}
        }

        if (info.name == null || info.name.length() == 0) {
            log("MANIFEST missing 'name'.");
            return;
        }
        if (info.version == null || info.version.length() == 0) {
            info.version = "0.0.0";
        }
        if (info.description == null) info.description = "";

        if (db.byName(info.name) != null) {
            log("Already installed: " + info.name);
            return;
        }

        // --- Dependency resolution ---
        try {
            DependencyResolver resolver = new DependencyResolver(db);
            DependencyResolver.Result res = resolver.resolve(info);
            if (!res.ok) {
                log("Dependency check failed:");
                log("  " + res.message);
                log("Aborting install. Database unchanged.");
                return;
            }
            log("Dependency check: " + res.message);
        } catch (Throwable t) {
            log("Dependency resolver error: " + t.getMessage());
            return;
        }

        // --- Copy files to $PREFIX ---
        File target = new File(env.getShareDir(), info.name);
        File srcTree = new File(pkgDir, "files");
        ArrayList<String> installedFiles = new ArrayList<String>();

        try {
            if (srcTree.isDirectory()) {
                copyRecursive(srcTree, target, installedFiles);
            }

            // Any executables in files/bin/ go to $PREFIX/bin
            File binSrc = new File(srcTree, "bin");
            if (binSrc.isDirectory()) {
                File[] kids = binSrc.listFiles();
                if (kids != null) {
                    for (int i = 0; i < kids.length; i++) {
                        File dst = new File(env.getBinDir(), kids[i].getName());
                        if (FileUtils.copy(kids[i], dst)) {
                            try { dst.setExecutable(true, false); }
                            catch (Throwable ignored) {}
                            installedFiles.add(dst.getAbsolutePath());
                        }
                    }
                }
            }

            File incSrc = new File(srcTree, "include");
            if (incSrc.isDirectory()) {
                copyRecursive(incSrc, new File(env.getPrefix(), "include"), installedFiles);
            }

            File libSrc = new File(srcTree, "lib");
            if (libSrc.isDirectory()) {
                copyRecursive(libSrc, env.getLibDir(), installedFiles);
            }

            File shareSrc = new File(srcTree, "share");
            if (shareSrc.isDirectory()) {
                copyRecursive(shareSrc, new File(env.getShareDir(), info.name), installedFiles);
            }
        } catch (Throwable t) {
            log("Install failed: " + t.getMessage());
            return;
        }

        info.files = installedFiles.toArray(new String[installedFiles.size()]);

        if (db.add(info)) {
            log("Installed " + info.name + " " + info.version);
            log("  Files copied: " + installedFiles.size());
        } else {
            log("Failed to record package in database.");
        }
    }

    // ------------------------------------------------------------
    // REMOVE
    // ------------------------------------------------------------

    public void remove(String name) {
        if (name == null || name.length() == 0) {
            log("Usage: zpkg remove <name>");
            return;
        }
        PackageInfo p = db.byName(name);
        if (p == null) { log("Not installed: " + name); return; }

        int deleted = 0;
        int failed = 0;
        if (p.files != null) {
            for (int i = 0; i < p.files.length; i++) {
                try {
                    File f = new File(p.files[i]);
                    if (f.exists()) {
                        if (f.delete()) deleted++;
                        else failed++;
                    }
                } catch (Throwable t) {
                    failed++;
                }
            }
        }
        db.remove(name);
        log("Removed " + name + " (files: " + deleted + ", kept: " + failed + ")");
    }

    // ------------------------------------------------------------
    // UPDATE / UPGRADE  (honest status messages)
    // ------------------------------------------------------------

    public void update() {
        log("Refreshing package index...");
        int repos = new ZpkgRepository(ctx).all().size();
        if (repos == 0) {
            log("");
            log("No repositories configured.");
            log("");
            log("zpkg does not download packages. Use: pkg install <name>");
            log("Local package database refreshed.");
            return;
        }
        log("Configured repositories: " + repos);
        log("");
        log("zpkg does not download packages. Use: pkg install <name>");
        log("Local package database refreshed.");
    }

    public void upgrade() {
        List<PackageInfo> all = db.all();
        if (all.isEmpty()) {
            log("No packages installed.");
            return;
        }
        log("Checking for upgrades...");
        log("");
        log("Upgrade sources are not configured.");
        log("Remote repository upgrades are planned for a future release.");
        log("");
        log("Local packages can be reinstalled with:");
        log("  zpkg install /path/to/package.zpkg");
    }

    // ------------------------------------------------------------
    // CLEAN / DOCTOR / REPO
    // ------------------------------------------------------------

    public void clean() {
        File cache = new File(env.getVarDir(), "cache/zpkg");
        if (!cache.isDirectory()) {
            cache.mkdirs();
            log("Package cache is empty.");
            return;
        }
        File[] kids = cache.listFiles();
        if (kids == null || kids.length == 0) {
            log("Package cache is already empty.");
            return;
        }
        if (FileUtils.deleteRecursive(cache)) {
            cache.mkdirs();
            log("Package cache cleaned.");
        } else {
            log("Failed to clean cache.");
        }
    }

    public void doctor() {
        log("zpkg doctor");
        log("  Database: " + env.getPkgDbFile().getAbsolutePath());
        log("  Cache:    " + new File(env.getVarDir(), "cache/zpkg").getAbsolutePath());
        List<PackageInfo> all = db.all();
        log("  Packages: " + all.size());

        int broken = 0;
        for (int i = 0; i < all.size(); i++) {
            PackageInfo p = all.get(i);
            if (p.files == null) continue;
            for (int j = 0; j < p.files.length; j++) {
                if (!new File(p.files[j]).exists()) { broken++; break; }
            }
        }
        log("  Broken:   " + broken);
        if (broken > 0) {
            log("  Run 'zpkg remove <name>' for broken packages.");
        }

        int repos = new ZpkgRepository(ctx).all().size();
        log("  Repos:    " + repos);

        log("");
        log("Note: zpkg v6.0.0 manages local packages only.");
        log("For real packages use: pkg install <name>");
        log("zpkg doctor complete.");
    }

    public void repoDispatch(String[] parts) {
        ZpkgRepository repo = new ZpkgRepository(ctx);
        if (parts.length < 3) {
            log("Usage: zpkg repo list|add <name> <url>|remove <name>|enable <name>|disable <name>");
            return;
        }
        String sub = parts[2];
        if (sub.equals("list")) {
            List<ZpkgRepository.Repo> all = repo.all();
            if (all.isEmpty()) { log("(no repositories configured)"); return; }
            for (int i = 0; i < all.size(); i++) {
                ZpkgRepository.Repo r = all.get(i);
                log(r.name + "  " + r.url + "  [" + (r.enabled ? "enabled" : "disabled") + "]");
            }
            log("");
            log("Note: this build does not download from repositories.");
            log("Remote fetching is planned for a future release.");
        } else if (sub.equals("add")) {
            if (parts.length < 5) { log("Usage: zpkg repo add <name> <url>"); return; }
            if (!parts[4].startsWith("https://")) {
                log("Refusing to add non-HTTPS repository.");
                return;
            }
            repo.add(new ZpkgRepository.Repo(parts[3], parts[4]));
            log("Added repository: " + parts[3]);
            log("Note: downloads are not enabled in this build.");
        } else if (sub.equals("remove")) {
            if (parts.length < 4) { log("Usage: zpkg repo remove <name>"); return; }
            repo.remove(parts[3]);
            log("Removed repository: " + parts[3]);
        } else if (sub.equals("enable")) {
            if (parts.length < 4) { log("Usage: zpkg repo enable <name>"); return; }
            repo.enable(parts[3], true);
            log("Enabled: " + parts[3]);
        } else if (sub.equals("disable")) {
            if (parts.length < 4) { log("Usage: zpkg repo disable <name>"); return; }
            repo.enable(parts[3], false);
            log("Disabled: " + parts[3]);
        } else {
            log("Unknown zpkg repo subcommand: " + sub);
        }
    }

    // ------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------

    private void copyRecursive(File src, File dst, List<String> acc) {
        if (src.isDirectory()) {
            if (!dst.exists()) dst.mkdirs();
            File[] kids = src.listFiles();
            if (kids != null) {
                for (int i = 0; i < kids.length; i++) {
                    copyRecursive(kids[i], new File(dst, kids[i].getName()), acc);
                }
            }
        } else {
            if (FileUtils.copy(src, dst)) acc.add(dst.getAbsolutePath());
        }
    }

    private void log(String s) {
        if (logger != null) logger.line(s);
    }
}
