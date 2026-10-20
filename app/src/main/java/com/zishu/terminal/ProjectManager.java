package com.zishu.terminal;

import android.content.Context;

import java.io.File;

/**
 * Simple project manager. Projects live under $HOME/projects/<name>.
 * Metadata is a plain file so v1 files remain readable.
 */
public class ProjectManager {

    public interface Logger { void line(String s); }

    private final ZishuEnv env;
    private final Logger logger;

    public ProjectManager(Context ctx, Logger logger) {
        this.env = new ZishuEnv(ctx);
        this.logger = logger;
        this.env.ensureAll();
    }

    public File root() { return env.getProjectsDir(); }

    public File projectDir(String name) { return new File(root(), name); }

    public boolean exists(String name) { return projectDir(name).isDirectory(); }

    public void list() {
        File[] kids = root().listFiles();
        if (kids == null || kids.length == 0) { log("(no projects)"); return; }
        for (int i = 0; i < kids.length; i++) {
            if (kids[i].isDirectory()) log(kids[i].getName());
        }
    }

    public void create(String name) {
        if (name == null || name.length() == 0) { log("Project name required."); return; }
        File dir = projectDir(name);
        if (dir.exists()) { log("Project already exists: " + name); return; }
        File src = new File(dir, "src");
        File assets = new File(dir, "assets");
        File build = new File(dir, "build");
        dir.mkdirs(); src.mkdirs(); assets.mkdirs(); build.mkdirs();
        try {
            FileUtils.writeText(new File(dir, "README.md"),
                    "# " + name + "\n\nCreated by Zishu Terminal v3.5.\n");
        } catch (Throwable ignored) {}
        log("Created project: " + dir.getAbsolutePath());
    }

    public void info(String name) {
        File dir = projectDir(name);
        if (!dir.isDirectory()) { log("Project not found: " + name); return; }
        log("Project: " + name);
        log("Path:    " + dir.getAbsolutePath());
        log("Modified:" + FileUtils.formatDate(dir.lastModified()));
    }

    public void delete(String name) {
        File dir = projectDir(name);
        if (!dir.isDirectory()) { log("Project not found: " + name); return; }
        if (FileUtils.deleteRecursive(dir)) log("Deleted project: " + name);
        else log("Failed to delete: " + name);
    }

    public File open(String name) { return projectDir(name); }

    private void log(String s) { if (logger != null) logger.line(s); }
}