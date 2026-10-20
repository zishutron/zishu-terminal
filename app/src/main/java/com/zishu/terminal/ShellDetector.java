package com.zishu.terminal;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects shells that actually exist on this device. Never reports
 * a shell that isn't present.
 */
public class ShellDetector {

    public static class Shell {
        public String name;
        public String path;
        public Shell(String name, String path) { this.name = name; this.path = path; }
    }

    public static List<Shell> available() {
        List<Shell> out = new ArrayList<Shell>();
        check(out, "sh",   new String[]{"/system/bin/sh"});
        check(out, "bash", new String[]{"/system/bin/bash", "/system/xbin/bash"});
        check(out, "zsh",  new String[]{"/system/bin/zsh", "/system/xbin/zsh"});
        check(out, "mksh", new String[]{"/system/bin/mksh"});
        return out;
    }

    public static String defaultShell() {
        File sh = new File("/system/bin/sh");
        if (sh.exists()) return "/system/bin/sh";
        return "/system/bin/sh"; // fallback
    }

    private static void check(List<Shell> out, String name, String[] paths) {
        for (int i = 0; i < paths.length; i++) {
            if (new File(paths[i]).exists()) {
                out.add(new Shell(name, paths[i]));
                return;
            }
        }
    }
}