package com.zishu.terminal;

import android.content.Context;
import android.os.Build;
import android.os.StatFs;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ZishuCommands {

    public interface Host {
        void print(String s);
        void clearScreen();
        String getCwd();
        void setCwd(String cwd);
        void showHistory();
        void showSessions();
        void showAbout();
        void showSettings();
        void showDiagnostics();
        void showPackages();
        void showProjects();
        void showTools();
        void showJobs();
        void showHelpTopic(String topic);
        void showServer();
        void runBackup();
        void runRestore();
        void openEditor(String path);
        void runCommand(String command);
        void startServer(String root, int port);
        void stopServer();
        void restartServer();
        String getServerStatus();
        void createJob(String name, String command);
    }

    private final Context ctx;
    private final Host host;
    private final ZishuEnv env;
    private final ZpkgManager zpkg;
    private final ProjectManager projects;

    public ZishuCommands(Context ctx, Host host) {
        this.ctx = ctx;
        this.host = host;
        this.env = new ZishuEnv(ctx);
        this.env.ensureAll();
        this.zpkg = new ZpkgManager(ctx, new ZpkgManager.Logger() {
            @Override public void line(String s) { host.print(s); }
        });
        this.projects = new ProjectManager(ctx, new ProjectManager.Logger() {
            @Override public void line(String s) { host.print(s); }
        });
    }

    // ============================================================
    // MAIN DISPATCH
    // ============================================================

    public boolean handle(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        if (trimmed.length() == 0) return false;
        String[] parts = trimmed.split("\\s+");
        String cmd = parts[0];

        if (cmd.equals("which"))    { which(parts); return true; }
        if (cmd.equals("env"))      { envDump(); return true; }
        if (cmd.equals("printenv")) { envDump(); return true; }

        if (!cmd.startsWith("z")) return false;

        if (cmd.equals("zbootstrap"))   { zbootstrap(parts); return true; }
        if (cmd.equals("zhelp"))        { help(parts); return true; }
        if (cmd.equals("zinfo"))        { info(); return true; }
        if (cmd.equals("zclear"))       { host.clearScreen(); return true; }
        if (cmd.equals("zversion"))     { host.print("Zishu Terminal v6.0.0"); return true; }
        if (cmd.equals("ztheme"))       { theme(parts); return true; }
        if (cmd.equals("zhistory"))     { host.showHistory(); return true; }
        if (cmd.equals("zsession"))     { sessionInfo(); return true; }
        if (cmd.equals("zstorage"))     { storage(); return true; }
        if (cmd.equals("zpkg"))         { zpkgDispatch(parts); return true; }
        if (cmd.equals("zabout"))       { host.showAbout(); return true; }
        if (cmd.equals("zpath"))        { path(); return true; }
        if (cmd.equals("zenv"))         { zenv(parts); return true; }
        if (cmd.equals("zwhich"))       { which(parts); return true; }
        if (cmd.equals("zdoctor"))      { doctor(); return true; }
        if (cmd.equals("zconfig"))      { host.showSettings(); return true; }
        if (cmd.equals("zproject"))     { projectDispatch(parts); return true; }
        if (cmd.equals("zrun"))         { zrun(parts); return true; }
        if (cmd.equals("zedit"))        { zedit(parts); return true; }
        if (cmd.equals("zserver"))      { zserver(parts); return true; }
        if (cmd.equals("zbackup"))      { host.runBackup(); return true; }
        if (cmd.equals("zrestore"))     { host.runRestore(); return true; }
        if (cmd.equals("zdiagnostics")) { host.showDiagnostics(); return true; }
        if (cmd.equals("zpackages"))    { host.showPackages(); return true; }
        if (cmd.equals("ztools"))       { host.showTools(); return true; }
        if (cmd.equals("zjobs"))        { host.showJobs(); return true; }
        if (cmd.equals("zfind"))        { zfind(parts); return true; }
        if (cmd.equals("zbuild"))       { zbuild(parts); return true; }
        if (cmd.equals("zprofile"))     { zprofileCmd(parts); return true; }
        if (cmd.equals("zbanner"))      { zbanner(parts); return true; }
        if (cmd.equals("zprompt"))      { zprompt(parts); return true; }
        return false;
    }

    // ============================================================
    // BOOTSTRAP (real userspace)
    // ============================================================

    private void zbootstrap(String[] parts) {
        final BootstrapManager bm = new BootstrapManager(ctx);
        String sub = parts.length > 1 ? parts[1] : "status";
        if (sub.equals("status")) {
            host.print("ABI:       " + String.valueOf(BootstrapManager.detectArch()));
            host.print("Userspace: " + (bm.isInstalled() ? "installed (bash, real binaries)" : "NOT installed (Android sh only)"));
            if (!bm.isInstalled()) host.print("Run: zbootstrap install");
        } else if (sub.equals("install")) {
            host.print("Installing bootstrap in background ...");
            final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
            new Thread(new Runnable() {
                @Override public void run() {
                    boolean ok = bm.install(new BootstrapManager.Log() {
                        @Override public void line(final String s) {
                            ui.post(new Runnable() { @Override public void run() { host.print(s); } });
                        }
                    });
                    final String done = ok ? "zbootstrap: OK" : "zbootstrap: FAILED";
                    ui.post(new Runnable() { @Override public void run() { host.print(done); } });
                }
            }).start();
        } else {
            host.print("Usage: zbootstrap [status|install]");
        }
    }

    // ============================================================
    // HELP
    // ============================================================

    private void help(String[] parts) {
        if (parts.length >= 2) { host.showHelpTopic(parts[1]); return; }
        host.print("Zishu Terminal v6.0.0 Commands");
        host.print("  zhelp [topic]   Show help");
        host.print("  zinfo           System information");
        host.print("  zclear          Clear terminal");
        host.print("  zversion        Show version");
        host.print("  ztheme          Theme settings");
        host.print("  zhistory        Command history");
        host.print("  zsession        Session information");
        host.print("  zstorage        Storage information");
        host.print("  zpath           Print Zishu paths");
        host.print("  zenv            Environment variables");
        host.print("  zwhich <cmd>    Locate a command");
        host.print("  zdoctor         Environment diagnostics");
        host.print("  ztools          List detected tools");
        host.print("  zjobs           List tracked jobs");
        host.print("  zfind <term>    Search files");
        host.print("  zbuild          Detect and build current project");
        host.print("  zpkg ...        Package manager");
        host.print("  zproject ...    Projects");
        host.print("  zedit <file>    Open editor");
        host.print("  zrun <script>   Run a shell script");
        host.print("  zserver ...     Local server");
        host.print("  zbackup         Backup");
        host.print("  zrestore        Restore");
        host.print("");
        host.print("Startup & Home:");
        host.print("  zbanner ...     Customize startup banner");
        host.print("  zprompt ...     Customize prompt");
        host.print("  zprofile ...    Manage ~/.zprofile");
        host.print("");
        host.print("Clipboard:");
        host.print("  zclip copy [N]      Copy output to clipboard");
        host.print("  zclip paste [input] Paste clipboard");
        host.print("  zclip help          Full zclip help");
        host.print("");
        host.print("Built-in commands:");
        host.print("  curl URL        Fetch URL (built-in, real HTTPS)");
        host.print("  zprotect ...    File encryption (AES-256-GCM)");
        host.print("  zgit clone ...  Clone public GitHub repo (snapshot)");
        host.print("");
        host.print("curl examples:");
        host.print("  curl https://example.com");
        host.print("  curl https://example.com > source.html");
        host.print("  curl -o file.html https://example.com");
        host.print("  curl -O https://example.com/file.txt");
        host.print("  curl -I https://example.com");
        host.print("  curl -L https://example.com");
        host.print("  curl --help");
        host.print("  curl --version");
        host.print("");
        host.print("zprotect examples:");
        host.print("  zprotect encrypt secret.txt --password mypass");
        host.print("  zprotect encrypt secret.txt --password mypass --delete");
        host.print("  zprotect decrypt secret.txt.zenc --password mypass");
        host.print("  zprotect info secret.txt.zenc");
        host.print("  zprotect verify secret.txt.zenc --password mypass");
        host.print("  zprotect list");
        host.print("  zprotect help");
        host.print("");
        host.print("zgit examples:");
        host.print("  zgit clone https://github.com/user/repo");
        host.print("  zgit clone https://github.com/user/repo target-dir");
        host.print("  zgit clone https://github.com/user/repo/tree/branch");
        host.print("  zgit version");
        host.print("  zgit help");
        host.print("");
        host.print("zbanner examples:");
        host.print("  zbanner on|off");
        host.print("  zbanner list");
        host.print("  zbanner set block|ascii|box|minimal|hacker|custom|none");
        host.print("  zbanner color accent|white|green|cyan|yellow|red");
        host.print("  zbanner custom <text>");
        host.print("  zbanner preview");
        host.print("  zbanner reset");
        host.print("");
        host.print("zprompt examples:");
        host.print("  zprompt show");
        host.print("  zprompt set \"[{user}] {cwd} $ \"");
        host.print("  zprompt reset");
        host.print("");
        host.print("zclip examples:");
        host.print("  zclip copy              Copy all output");
        host.print("  zclip copy 10           Copy last 10 lines");
        host.print("  zclip copy line 5       Copy line 5");
        host.print("  zclip copy 5 10         Copy lines 5..10");
        host.print("  zclip paste             Print clipboard");
        host.print("  zclip paste input       Insert clipboard into input");
        host.print("  zclip save ~/clip.txt   Save clipboard to file");
        host.print("  zclip load ~/clip.txt   Load file into clipboard");
        host.print("  zclip history           Last 10 clipboard items");
        host.print("  zclip help              Full help");
    }

    // ============================================================
    // INFO
    // ============================================================

    private void info() {
        host.print("Zishu Terminal v6.0.0");
        host.print("Device: " + safe(Build.MANUFACTURER) + " " + safe(Build.MODEL));
        host.print("Android: " + safe(Build.VERSION.RELEASE) + " (SDK " + Build.VERSION.SDK_INT + ")");
        String abi = (Build.SUPPORTED_ABIS.length > 0) ? Build.SUPPORTED_ABIS[0] : "unknown";
        host.print("ABI: " + abi);
        host.print("PREFIX: " + env.getPrefix().getAbsolutePath());
        host.print("HOME:   " + env.getHome().getAbsolutePath());
        host.print("PATH:   " + env.asMap().get("PATH"));
        try {
            PackageDatabase db = new PackageDatabase(ctx);
            host.print("Packages installed: " + db.all().size());
        } catch (Throwable t) {
            host.print("Packages installed: ?");
        }
        host.print("Projects: " + countProjects());
        host.print("Shell: " + ShellDetector.defaultShell());
    }

    private int countProjects() {
        File[] kids = env.getProjectsDir().listFiles();
        if (kids == null) return 0;
        int n = 0;
        for (int i = 0; i < kids.length; i++) if (kids[i].isDirectory()) n++;
        return n;
    }

    // ============================================================
    // PATHS & ENV
    // ============================================================

    private void path() {
        host.print("PREFIX = " + env.getPrefix().getAbsolutePath());
        host.print("HOME   = " + env.getHome().getAbsolutePath());
        host.print("TMPDIR = " + env.getTmp().getAbsolutePath());
        host.print("BIN    = " + env.getBinDir().getAbsolutePath());
    }

    private void envDump() {
        Map<String, String> m = env.asMap();
        for (Map.Entry<String, String> e : m.entrySet()) {
            host.print(e.getKey() + "=" + e.getValue());
        }
    }

    private void zenv(String[] parts) {
        if (parts.length < 2 || parts[1].equals("list")) { envDump(); return; }
        String sub = parts[1];
        if (sub.equals("get")) {
            if (parts.length < 3) { host.print("Usage: zenv get NAME"); return; }
            String v = env.asMap().get(parts[2]);
            host.print(v == null ? "(unset)" : v);
        } else if (sub.equals("set")) {
            if (parts.length < 4) { host.print("Usage: zenv set NAME VALUE"); return; }
            StringBuilder v = new StringBuilder();
            for (int i = 3; i < parts.length; i++) {
                if (i > 3) v.append(' ');
                v.append(parts[i]);
            }
            Prefs.get(ctx).edit().putString("zenv." + parts[2], v.toString()).apply();
            host.print("Set " + parts[2] + "=" + v);
        } else if (sub.equals("unset")) {
            if (parts.length < 3) { host.print("Usage: zenv unset NAME"); return; }
            Prefs.get(ctx).edit().remove("zenv." + parts[2]).apply();
            host.print("Unset " + parts[2]);
        } else if (sub.equals("snapshot")) {
            envDump();
            host.print("---");
            host.print("Snapshot complete.");
        } else {
            host.print("Unknown zenv subcommand: " + sub);
        }
    }

    private void which(String[] parts) {
        if (parts.length < 2) { host.print("Usage: which <command>"); return; }
        String name = parts[1];

        if ("curl".equals(name)) {
            File marker = new File(env.getBinDir(), "curl");
            if (marker.exists()) { host.print(marker.getAbsolutePath()); return; }
        }
        if ("zprotect".equals(name)) {
            File marker = new File(env.getBinDir(), "zprotect");
            if (marker.exists()) { host.print(marker.getAbsolutePath()); return; }
        }

        String p = ToolRegistry.findInPath(env, name);
        if (p == null) host.print(name + ": not found");
        else host.print(p);
    }

    // ============================================================
    // DOCTOR
    // ============================================================

    private void doctor() {
        ZishuDoctor d = new ZishuDoctor(ctx, new ZishuDoctor.Logger() {
            @Override public void line(String s) { host.print(s); }
        });
        d.run();
    }

    // ============================================================
    // THEME
    // ============================================================

    private void theme(String[] parts) {
        if (parts.length < 2) {
            int cur = Prefs.getInt(ctx, Prefs.KEY_THEME, Prefs.DEFAULT_THEME);
            host.print("Current theme: " + themeName(cur));
            host.print("Usage: ztheme [dark|light|green]");
            return;
        }
        String arg = parts[1].toLowerCase(Locale.US);
        int t;
        if (arg.equals("dark")) t = Prefs.THEME_DARK;
        else if (arg.equals("light")) t = Prefs.THEME_LIGHT;
        else if (arg.equals("green")) t = Prefs.THEME_GREEN;
        else { host.print("Unknown theme: " + arg); return; }
        Prefs.putInt(ctx, Prefs.KEY_THEME, t);
        host.print("Theme set to " + themeName(t) + ".");
    }

    private String themeName(int t) {
        if (t == Prefs.THEME_LIGHT) return "Light";
        if (t == Prefs.THEME_GREEN) return "Soft Green";
        return "Dark";
    }

    // ============================================================
    // SESSION
    // ============================================================

    private void sessionInfo() {
        host.print("Session: " + SessionManager.get().getActiveId());
        host.print("Working directory: " + host.getCwd());
        host.print("Total sessions: " + SessionManager.get().all().size());
    }

    // ============================================================
    // STORAGE
    // ============================================================

    private void storage() {
        try {
            File files = ctx.getFilesDir();
            StatFs fs = new StatFs(files.getAbsolutePath());
            long bs = fs.getBlockSizeLong();
            host.print("App data: " + files.getAbsolutePath());
            host.print("Available: " + formatBytes(fs.getAvailableBlocksLong() * bs));
            host.print("Total:     " + formatBytes(fs.getBlockCountLong() * bs));
        } catch (Throwable t) {
            host.print("Storage info unavailable.");
        }
    }

    // ============================================================
    // ZPKG
    // ============================================================

    private void zpkgDispatch(String[] parts) {
        if (parts.length < 2) { zpkg.help(); return; }
        String sub = parts[1];
        if (sub.equals("help"))              zpkg.help();
        else if (sub.equals("list"))         zpkg.list();
        else if (sub.equals("installed"))    zpkg.installed();
        else if (sub.equals("info")) {
            if (parts.length < 3) { host.print("Usage: zpkg info <name>"); return; }
            zpkg.info(parts[2]);
        }
        else if (sub.equals("search")) {
            if (parts.length < 3) { host.print("Usage: zpkg search <term>"); return; }
            zpkg.search(parts[2]);
        }
        else if (sub.equals("install")) {
            if (parts.length < 3) { host.print("Usage: zpkg install <dir>"); return; }
            zpkg.install(parts[2]);
        }
        else if (sub.equals("remove")) {
            if (parts.length < 3) { host.print("Usage: zpkg remove <name>"); return; }
            zpkg.remove(parts[2]);
        }
        else if (sub.equals("update"))       zpkg.update();
        else if (sub.equals("upgrade"))      zpkg.upgrade();
        else if (sub.equals("clean"))        zpkg.clean();
        else if (sub.equals("doctor"))       zpkg.doctor();
        else if (sub.equals("repo"))         zpkg.repoDispatch(parts);
        else host.print("Unknown zpkg subcommand: " + sub);
    }

    // ============================================================
    // PROJECTS
    // ============================================================

    private void projectDispatch(String[] parts) {
        if (parts.length < 2) {
            host.print("Usage: zproject create|list|info|open|run|build|clean|delete [name]");
            return;
        }
        String sub = parts[1];
        if (sub.equals("list")) projects.list();
        else if (sub.equals("create")) {
            if (parts.length < 3) { host.print("Usage: zproject create <name>"); return; }
            projects.create(parts[2]);
        }
        else if (sub.equals("info")) {
            if (parts.length < 3) { host.print("Usage: zproject info <name>"); return; }
            projects.info(parts[2]);
        }
        else if (sub.equals("open")) {
            if (parts.length < 3) { host.print("Usage: zproject open <name>"); return; }
            File dir = projects.open(parts[2]);
            if (dir.isDirectory()) {
                host.setCwd(dir.getAbsolutePath());
                host.print("cwd: " + dir.getAbsolutePath());
            } else host.print("Project not found: " + parts[2]);
        }
        else if (sub.equals("delete")) {
            if (parts.length < 3) { host.print("Usage: zproject delete <name>"); return; }
            projects.delete(parts[2]);
        }
        else if (sub.equals("run")) {
            if (parts.length < 3) { host.print("Usage: zproject run <name>"); return; }
            File dir = projects.open(parts[2]);
            if (!dir.isDirectory()) { host.print("Project not found: " + parts[2]); return; }
            String cmd = BuildSystem.runCommand(dir, env);
            if (cmd == null) {
                host.print("No run command available for this project.");
                host.print("Add run.sh, main.py or index.js.");
                return;
            }
            host.setCwd(dir.getAbsolutePath());
            host.createJob("project:" + parts[2], cmd);
            host.runCommand(cmd);
        }
        else if (sub.equals("build")) {
            if (parts.length < 3) { host.print("Usage: zproject build <name>"); return; }
            zbuildProject(parts[2]);
        }
        else if (sub.equals("clean")) {
            if (parts.length < 3) { host.print("Usage: zproject clean <name>"); return; }
            File dir = projects.open(parts[2]);
            File build = new File(dir, "build");
            if (FileUtils.deleteRecursive(build)) host.print("Cleaned: " + parts[2]);
            else host.print("Nothing to clean.");
        }
        else host.print("Unknown zproject subcommand: " + sub);
    }

    // ============================================================
    // ZRUN
    // ============================================================

    private void zrun(String[] parts) {
        if (parts.length < 2) {
            host.print("Usage: zrun <script.sh>");
            return;
        }
        File f = new File(host.getCwd(), parts[1]);
        if (!f.exists()) { host.print("Not found: " + parts[1]); return; }
        if (!f.isFile()) { host.print("Not a file: " + parts[1]); return; }
        try { f.setReadable(true, false); } catch (Throwable ignored) {}
        host.createJob("zrun:" + parts[1], "sh " + f.getAbsolutePath());
        host.runCommand("sh " + f.getAbsolutePath());
    }

    // ============================================================
    // ZEDIT
    // ============================================================

    private void zedit(String[] parts) {
        if (parts.length < 2) { host.openEditor(""); return; }
        File f = new File(host.getCwd(), parts[1]);
        host.openEditor(f.getAbsolutePath());
    }

    // ============================================================
    // ZSERVER
    // ============================================================

    private void zserver(String[] parts) {
        if (parts.length < 2 || parts[1].equals("status")) {
            host.print("Server status: " + host.getServerStatus());
            host.print("Usage: zserver start [root] [port] | stop | restart | status | logs");
            return;
        }
        String sub = parts[1];
        if (sub.equals("stop")) { host.stopServer(); return; }
        if (sub.equals("restart")) { host.restartServer(); return; }
        if (sub.equals("logs")) { host.showServer(); return; }
        if (sub.equals("start")) {
            String root = host.getCwd();
            if (parts.length >= 3) root = expandZserverPath(parts[2]);
            int port = 8080;
            if (parts.length >= 4) {
                try {
                    port = Integer.parseInt(parts[3]);
                    if (port < 1 || port > 65535) { host.print("Invalid port: " + parts[3]); return; }
                } catch (Throwable t) { host.print("Invalid port: " + parts[3]); return; }
            }
            File rootFile = new File(root);
            if (!rootFile.exists()) { host.print("Root does not exist: " + root); host.print("Tip: pass a directory, not a file"); return; }
            if (!rootFile.isDirectory()) { host.print("Root is not a directory: " + root); host.print("Tip: server root must be a folder"); return; }
            host.startServer(rootFile.getAbsolutePath(), port);
            return;
        }
        host.print("Unknown zserver subcommand: " + sub);
    }

    private String expandZserverPath(String value) {
        if (value == null || value.length() == 0) return value;
        String v = value;
        try {
            String home = env.getHome().getAbsolutePath();
            String prefix = env.getPrefix().getAbsolutePath();
            String tmp = env.getTmp().getAbsolutePath();
            v = v.replace("${HOME}", home); v = v.replace("${PREFIX}", prefix); v = v.replace("${TMPDIR}", tmp);
            v = v.replace("$HOME", home); v = v.replace("$PREFIX", prefix); v = v.replace("$TMPDIR", tmp);
            if (v.equals("~")) v = home;
            else if (v.startsWith("~/")) v = home + v.substring(1);
        } catch (Throwable ignored) {}
        return v;
    }

    // ============================================================
    // ZBUILD
    // ============================================================

    private void zbuild(String[] parts) {
        String name = (parts.length >= 2) ? parts[1] : null;
        if (name == null) {
            File cwd = new File(host.getCwd());
            BuildSystem.Plan p = BuildSystem.detect(cwd, env);
            host.print("Project type: " + p.type);
            host.print(p.message);
            if (p.command != null) {
                host.createJob("build:" + cwd.getName(), p.command);
                host.runCommand(p.command);
            }
        } else {
            zbuildProject(name);
        }
    }

    private void zbuildProject(String name) {
        File dir = projects.open(name);
        if (!dir.isDirectory()) { host.print("Project not found: " + name); return; }
        BuildSystem.Plan p = BuildSystem.detect(dir, env);
        host.print("Project type: " + p.type);
        host.print(p.message);
        if (p.command != null) {
            host.setCwd(dir.getAbsolutePath());
            host.createJob("build:" + name, p.command);
            host.runCommand(p.command);
        }
    }

    // ============================================================
    // ZFIND
    // ============================================================

    private void zfind(String[] parts) {
        if (parts.length < 2) { host.print("Usage: zfind <term>"); return; }
        final String term = parts[1];
        final File cwd = new File(host.getCwd());
        host.print("Searching...");
        new Thread(new Runnable() {
            @Override public void run() {
                List<SearchEngine.Hit> hits = SearchEngine.search(cwd, term, true, 200, null);
                final List<SearchEngine.Hit> h = hits;
                android.os.Handler main = new android.os.Handler(
                        android.os.Looper.getMainLooper());
                main.post(new Runnable() {
                    @Override public void run() {
                        if (h.isEmpty()) { host.print("No matches."); return; }
                        for (int i = 0; i < h.size(); i++) {
                            SearchEngine.Hit x = h.get(i);
                            host.print(x.file.getAbsolutePath() + ":" + x.line + ": " + x.text);
                        }
                    }
                });
            }
        }).start();
    }

    // ============================================================
    // ZPROFILE (old - profile manager)
    // ============================================================

    private void zprofile(String[] parts) {
        ProfileManager pm = new ProfileManager(ctx);
        if (parts.length < 2 || parts[1].equals("list")) {
            List<TerminalProfile> all = pm.all();
            for (int i = 0; i < all.size(); i++) host.print(all.get(i).name);
            return;
        }
        String sub = parts[1];
        if (sub.equals("show")) {
            if (parts.length < 3) { host.print("Usage: zprofile show <name>"); return; }
            TerminalProfile p = pm.load(parts[2]);
            host.print("Name:      " + p.name);
            host.print("Font size: " + p.fontSize);
            host.print("Theme:     " + themeName(p.theme));
        } else if (sub.equals("use")) {
            if (parts.length < 3) { host.print("Usage: zprofile use <name>"); return; }
            TerminalProfile p = pm.load(parts[2]);
            Prefs.putInt(ctx, Prefs.KEY_FONT_SIZE, p.fontSize);
            Prefs.putInt(ctx, Prefs.KEY_THEME, p.theme);
            host.print("Applied profile: " + p.name);
        } else {
            host.print("Unknown zprofile subcommand: " + sub);
        }
    }

    // ============================================================
    // ZBANNER
    // ============================================================

    private void zbanner(String[] parts) {
        if (parts.length < 2) {
            host.print("Startup banner: " + (StartupPrefs.isBannerEnabled(ctx) ? "ON" : "OFF"));
            host.print("Style: " + StartupPrefs.bannerStyleName(StartupPrefs.getBannerStyle(ctx)));
            host.print("Color: " + StartupPrefs.bannerColorName(StartupPrefs.getBannerColor(ctx)));
            host.print("");
            host.print("Usage:");
            host.print("  zbanner on|off             Enable/disable banner");
            host.print("  zbanner list               List built-in styles");
            host.print("  zbanner set <style>        Set banner style");
            host.print("  zbanner color <color>      Set banner color");
            host.print("  zbanner custom <text>      Set custom banner text");
            host.print("  zbanner reset              Reset to default");
            host.print("  zbanner preview            Preview current banner");
            return;
        }
        String sub = parts[1].toLowerCase(Locale.US);

        if (sub.equals("on")) { StartupPrefs.setBannerEnabled(ctx, true); host.print("Banner enabled."); return; }
        if (sub.equals("off")) { StartupPrefs.setBannerEnabled(ctx, false); host.print("Banner disabled."); return; }
        if (sub.equals("list")) {
            host.print("Built-in banner styles:");
            host.print("  block      - Unicode block (default)");
            host.print("  ascii      - Classic ASCII art");
            host.print("  box        - Box style");
            host.print("  minimal    - Minimal one-liner");
            host.print("  hacker     - Hacker style");
            host.print("  custom     - User-provided");
            host.print("  none       - No banner");
            return;
        }
        if (sub.equals("set")) {
            if (parts.length < 3) { host.print("Usage: zbanner set <style>"); return; }
            String style = parts[2].toLowerCase(Locale.US);
            int v = -1;
            if (style.equals("block"))        v = StartupPrefs.BANNER_BLOCK;
            else if (style.equals("ascii"))   v = StartupPrefs.BANNER_ASCII;
            else if (style.equals("box"))     v = StartupPrefs.BANNER_BOX;
            else if (style.equals("minimal")) v = StartupPrefs.BANNER_MINIMAL;
            else if (style.equals("hacker"))  v = StartupPrefs.BANNER_HACKER;
            else if (style.equals("custom"))  v = StartupPrefs.BANNER_CUSTOM;
            else if (style.equals("none"))    v = StartupPrefs.BANNER_NONE;
            if (v < 0) { host.print("Unknown style: " + style); host.print("Try: zbanner list"); return; }
            StartupPrefs.setBannerStyle(ctx, v);
            host.print("Banner style set to: " + StartupPrefs.bannerStyleName(v));
            return;
        }
        if (sub.equals("color")) {
            if (parts.length < 3) { host.print("Usage: zbanner color <color>"); host.print("Colors: accent, white, green, cyan, yellow, red"); return; }
            String color = parts[2].toLowerCase(Locale.US);
            int v = -1;
            if (color.equals("accent"))       v = StartupPrefs.COLOR_ACCENT;
            else if (color.equals("white"))   v = StartupPrefs.COLOR_WHITE;
            else if (color.equals("green"))   v = StartupPrefs.COLOR_GREEN;
            else if (color.equals("cyan"))    v = StartupPrefs.COLOR_CYAN;
            else if (color.equals("yellow"))  v = StartupPrefs.COLOR_YELLOW;
            else if (color.equals("red"))     v = StartupPrefs.COLOR_RED;
            if (v < 0) { host.print("Unknown color: " + color); return; }
            StartupPrefs.setBannerColor(ctx, v);
            host.print("Banner color set to: " + StartupPrefs.bannerColorName(v));
            return;
        }
        if (sub.equals("custom")) {
            if (parts.length < 3) {
                String cur = StartupPrefs.getCustomBanner(ctx);
                if (cur.length() == 0) host.print("(currently empty)");
                else { host.print("Current custom banner:"); host.print(cur); }
                host.print(""); host.print("Usage: zbanner custom <text>");
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < parts.length; i++) { if (i > 2) sb.append(' '); sb.append(parts[i]); }
            StartupPrefs.setCustomBanner(ctx, sb.toString());
            host.print("Custom banner saved.");
            return;
        }
        if (sub.equals("reset")) {
            StartupPrefs.setBannerEnabled(ctx, StartupPrefs.DEFAULT_BANNER_ENABLED);
            StartupPrefs.setBannerStyle(ctx, StartupPrefs.DEFAULT_BANNER_STYLE);
            StartupPrefs.setBannerColor(ctx, StartupPrefs.DEFAULT_BANNER_COLOR);
            host.print("Banner reset to defaults.");
            return;
        }
        if (sub.equals("preview")) {
            String banner = BannerRenderer.render(ctx);
            if (banner == null) host.print("(banner is disabled)");
            else {
                String[] lines = banner.split("\n", -1);
                for (int i = 0; i < lines.length; i++) host.print(lines[i]);
            }
            return;
        }
        host.print("Unknown zbanner subcommand: " + sub);
    }

    // ============================================================
    // ZPROMPT
    // ============================================================

    private void zprompt(String[] parts) {
        if (parts.length < 2) {
            host.print("Current prompt format: " + getPromptFormat());
            host.print("");
            host.print("Usage:");
            host.print("  zprompt show              Show current format");
            host.print("  zprompt set <format>      Set prompt format");
            host.print("  zprompt reset             Reset to default");
            host.print("");
            host.print("Format variables:");
            host.print("  {user}   - username (zishu)");
            host.print("  {cwd}    - current directory");
            host.print("  {time}   - current time");
            host.print("");
            host.print("Example:");
            host.print("  zprompt set \"[{user}] {cwd} $ \"");
            return;
        }
        String sub = parts[1].toLowerCase(Locale.US);
        if (sub.equals("show")) { host.print("Prompt format: " + getPromptFormat()); return; }
        if (sub.equals("set")) {
            if (parts.length < 3) { host.print("Usage: zprompt set <format>"); return; }
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < parts.length; i++) { if (i > 2) sb.append(' '); sb.append(parts[i]); }
            String fmt = sb.toString();
            if (fmt.length() >= 2) {
                char c0 = fmt.charAt(0);
                char c1 = fmt.charAt(fmt.length() - 1);
                if ((c0 == '"' && c1 == '"') || (c0 == '\'' && c1 == '\'')) fmt = fmt.substring(1, fmt.length() - 1);
            }
            Prefs.putString(ctx, "prompt_format", fmt);
            host.print("Prompt format set to: " + fmt);
            host.print("(open a new session to apply)");
            return;
        }
        if (sub.equals("reset")) {
            Prefs.get(ctx).edit().remove("prompt_format").apply();
            host.print("Prompt reset to default.");
            return;
        }
        host.print("Unknown zprompt subcommand: " + sub);
    }

    private String getPromptFormat() {
        String fmt = Prefs.getString(ctx, "prompt_format", null);
        if (fmt == null || fmt.length() == 0) return "zishu@android:{cwd} $ ";
        return fmt;
    }

    // ============================================================
    // ZPROFILE CMD (manage ~/.zprofile)
    // ============================================================

    private void zprofileCmd(String[] parts) {
        File zprofile = new File(env.getHome(), ".zprofile");
        if (parts.length < 2) {
            host.print("~/.zprofile: " + zprofile.getAbsolutePath());
            host.print("Exists: " + zprofile.isFile());
            host.print("");
            host.print("Usage:");
            host.print("  zprofile show      Show current content");
            host.print("  zprofile path      Print the file path");
            host.print("  zprofile reload    Re-run the profile");
            host.print("  zprofile reset     Restore default profile");
            return;
        }
        String sub = parts[1].toLowerCase(Locale.US);
        if (sub.equals("path")) { host.print(zprofile.getAbsolutePath()); return; }
        if (sub.equals("show")) {
            if (!zprofile.isFile()) { host.print("(file does not exist yet)"); return; }
            try {
                java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(new java.io.FileInputStream(zprofile), "UTF-8"));
                String line;
                while ((line = r.readLine()) != null) host.print(line);
                r.close();
            } catch (Throwable t) { host.print("Error reading: " + t.getMessage()); }
            return;
        }
        if (sub.equals("reload")) {
            host.runCommand(". \"" + zprofile.getAbsolutePath() + "\"");
            host.print("Reloaded ~/.zprofile");
            return;
        }
        if (sub.equals("reset")) {
            if (zprofile.exists()) zprofile.delete();
            host.print("~/.zprofile deleted. It will be recreated on next session.");
            return;
        }
        host.print("Unknown zprofile subcommand: " + sub);
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private static String safe(String s) { return s == null ? "unknown" : s; }

    private static String formatBytes(long b) {
        if (b < 1024) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    public String timestamp() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }
}
