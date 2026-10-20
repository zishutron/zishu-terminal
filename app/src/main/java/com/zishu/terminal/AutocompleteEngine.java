package com.zishu.terminal;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Local autocomplete: known builtins + files in cwd. No network.
 */
public class AutocompleteEngine {

    private static final String[] BUILTINS = new String[]{
            "pwd", "ls", "cd", "echo", "cat", "mkdir", "rmdir", "rm", "cp", "mv",
            "touch", "head", "tail", "grep", "find", "which", "whoami", "id",
            "date", "uname", "getprop", "env", "printenv", "clear", "exit", "sh",
            "zhelp", "zinfo", "zclear", "zversion", "ztheme", "zhistory",
            "zsession", "zstorage", "zpkg", "zabout", "zpath", "zenv", "zwhich",
            "zdoctor", "zconfig", "zproject", "zrun", "zedit", "zserver"
    };

    /** Return best single suggestion for current partial input, or null. */
    public static String suggest(String partial, String cwd) {
        if (partial == null) return null;
        String p = partial.trim();
        if (p.length() == 0) return null;

        // No space yet -> complete command name
        if (p.indexOf(' ') < 0) {
            for (int i = 0; i < BUILTINS.length; i++) {
                if (BUILTINS[i].startsWith(p)) return BUILTINS[i];
            }
            // Also try files in cwd whose name starts with p
            File dir = new File(cwd);
            File[] kids = dir.listFiles();
            if (kids != null) {
                for (int i = 0; i < kids.length; i++) {
                    String n = kids[i].getName();
                    if (n.startsWith(p)) return n;
                }
            }
            return null;
        }

        // After command + space -> complete path
        int sp = p.lastIndexOf(' ');
        String head = p.substring(0, sp + 1);
        String tail = p.substring(sp + 1);
        if (tail.length() == 0) return null;

        File baseDir;
        String namePrefix;
        if (tail.startsWith("/")) {
            baseDir = new File("/");
            namePrefix = tail.substring(1);
        } else {
            baseDir = new File(cwd);
            namePrefix = tail;
        }
        File[] kids = baseDir.listFiles();
        if (kids == null) return null;
        for (int i = 0; i < kids.length; i++) {
            String n = kids[i].getName();
            if (n.startsWith(namePrefix)) {
                String full = head + (tail.startsWith("/") ? "/" : "") + n;
                return full;
            }
        }
        return null;
    }

    public static List<String> allSuggestions(String partial, String cwd) {
        List<String> out = new ArrayList<String>();
        if (partial == null) return out;
        String p = partial.trim();
        for (int i = 0; i < BUILTINS.length; i++) {
            if (BUILTINS[i].startsWith(p)) out.add(BUILTINS[i]);
        }
        return out;
    }
}