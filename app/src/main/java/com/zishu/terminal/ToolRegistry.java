package com.zishu.terminal;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects developer tools in Zishu bin + Android system paths.
 * Never lies about a tool being available.
 */
public class ToolRegistry {

    public static class Tool {
        public String name;
        public String path;       // null if not found
        public String category;   // "zishu", "system", "toolchain"
        public boolean installed; // true if path found AND executable
    }

    public static List<Tool> detect(ZishuEnv env) {
        List<Tool> out = new ArrayList<Tool>();

        // Core shell tools (system)
        add(out, env, "sh", "system");
        add(out, env, "ls", "system");
        add(out, env, "cat", "system");
        add(out, env, "cp", "system");
        add(out, env, "mv", "system");
        add(out, env, "rm", "system");
        add(out, env, "mkdir", "system");
        add(out, env, "grep", "system");
        add(out, env, "find", "system");
        add(out, env, "sort", "system");
        add(out, env, "head", "system");
        add(out, env, "tail", "system");
        add(out, env, "wc", "system");
        add(out, env, "echo", "system");

        // Developer toolchains (likely NOT installed)
        add(out, env, "python", "toolchain");
        add(out, env, "python3", "toolchain");
        add(out, env, "pip", "toolchain");
        add(out, env, "git", "toolchain");
        add(out, env, "ssh", "toolchain");
        add(out, env, "scp", "toolchain");
        add(out, env, "node", "toolchain");
        add(out, env, "npm", "toolchain");
        add(out, env, "java", "toolchain");
        add(out, env, "javac", "toolchain");
        add(out, env, "clang", "toolchain");
        add(out, env, "gcc", "toolchain");
        add(out, env, "make", "toolchain");
        add(out, env, "cmake", "toolchain");

        return out;
    }

    private static void add(List<Tool> out, ZishuEnv env, String name, String cat) {
        Tool t = new Tool();
        t.name = name;
        t.category = cat;
        String path = findInPath(env, name);
        t.path = path;
        t.installed = (path != null);
        out.add(t);
    }

    public static String findInPath(ZishuEnv env, String name) {
        String[] dirs = new String[]{
                env.getBinDir().getAbsolutePath(),
                "/system/bin",
                "/system/xbin",
                "/system/sbin",
                "/sbin"
        };
        for (int i = 0; i < dirs.length; i++) {
            File f = new File(dirs[i], name);
            if (f.isFile() && f.canExecute()) return f.getAbsolutePath();
        }
        return null;
    }
}