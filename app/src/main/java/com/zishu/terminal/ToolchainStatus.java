package com.zishu.terminal;

/**
 * Reports toolchain availability honestly. Nothing here is faked:
 * if a binary is not present, we say so.
 */
public class ToolchainStatus {

    public static class Status {
        public String tool;
        public String displayName;
        public boolean installed;
        public String path;
        public String note; // e.g. "requires package"
    }

    public static Status[] all(ZishuEnv env) {
        return new Status[]{
                check(env, "sh", "Shell"),
                check(env, "ls", "ls"),
                check(env, "grep", "grep"),
                check(env, "git", "Git"),
                check(env, "ssh", "OpenSSH client"),
                check(env, "scp", "SCP"),
                check(env, "python", "Python 3"),
                check(env, "python3", "Python 3 (alt)"),
                check(env, "pip", "pip"),
                check(env, "node", "Node.js"),
                check(env, "npm", "npm"),
                check(env, "java", "Java runtime"),
                check(env, "javac", "Java compiler"),
                check(env, "clang", "Clang"),
                check(env, "gcc", "GCC"),
                check(env, "make", "make"),
                check(env, "cmake", "CMake")
        };
    }

    /** Honest install hint for a missing tool. */
    public static String hint(String name) {
        String pkg;
        if (name.equals("node")) pkg = "nodejs-lts";
        else if (name.equals("npm")) pkg = "npm";
        else if (name.equals("git")) pkg = "git";
        else if (name.equals("ssh") || name.equals("scp")) pkg = "openssh";
        else if (name.equals("python") || name.equals("python3")) pkg = "python";
        else if (name.equals("pip")) pkg = "python-pip";
        else if (name.equals("clang") || name.equals("gcc")) pkg = "clang";
        else if (name.equals("make")) pkg = "make";
        else if (name.equals("cmake")) pkg = "cmake";
        else if (name.equals("java") || name.equals("javac")) return "not available yet (no Java package in the repo)";
        else return "not installed";
        return "not installed - run: pkg install " + pkg;
    }

    private static Status check(ZishuEnv env, String name, String display) {
        Status s = new Status();
        s.tool = name;
        s.displayName = display;
        String path = ToolRegistry.findInPath(env, name);
        s.installed = (path != null);
        s.path = path;
        s.note = s.installed ? "" : hint(name);
        return s;
    }
}
