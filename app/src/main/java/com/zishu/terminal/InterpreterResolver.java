package com.zishu.terminal;

import java.io.File;

/**
 * Resolves an interpreter name (e.g. "bash", "python3") to an absolute
 * path, searching standard locations.
 *
 * Search order:
 *   1. $PREFIX/bin/<name>          (Zishu userspace)
 *   2. /system/bin/<name>          (Android default)
 *   3. /system/xbin/<name>
 *   4. /system/sbin/<name>
 *   5. /sbin/<name>
 *   6. /vendor/bin/<name>
 *
 * Special mapping (Android reality):
 *   "sh"      → /system/bin/sh  (Android has no /bin/sh)
 *   "bash"    → search all locations; no fallback
 *   "python"  → search all locations; no fallback
 *
 * Never silently substitutes one interpreter for another.
 * If the requested interpreter is not found, returns null.
 *
 * Traditional Java 6/7 syntax. No lambdas. No AndroidX.
 */
public final class InterpreterResolver {

    private static final String[] SEARCH_DIRS = new String[] {
            // Filled dynamically at runtime (prefix + system dirs)
    };

    /**
     * Resolves interpreter name to absolute path.
     * Returns null if not found.
     */
    public static String resolve(ZishuEnv env, String interpreter) {
        if (interpreter == null || interpreter.length() == 0) return null;

        // Sanitize — only allow [a-zA-Z0-9._-]
        if (!isSafeName(interpreter)) return null;

        // Special-case: "sh" always maps to Android's shell if present.
        if (interpreter.equals("sh")) {
            File sysSh = new File("/system/bin/sh");
            if (sysSh.isFile()) return sysSh.getAbsolutePath();
            // Fall through to general search if /system/bin/sh missing
        }

        // Build search path list dynamically
        String[] dirs = buildSearchDirs(env);

        for (int i = 0; i < dirs.length; i++) {
            File candidate = new File(dirs[i], interpreter);
            if (candidate.isFile()) {
                // For scripts, we also want them executable. But some
                // interpreters are shell scripts themselves (e.g. wrappers).
                // We accept any regular file.
                return candidate.getAbsolutePath();
            }
        }

        return null;
    }

    /**
     * Same as resolve(env, name) but with env == null.
     * Searches only system dirs.
     */
    public static String resolve(String interpreter) {
        return resolve(null, interpreter);
    }

    private static String[] buildSearchDirs(ZishuEnv env) {
        // Keep a stable, small set. env may add $PREFIX/bin.
        String prefixBin = null;
        if (env != null) {
            try {
                File bin = env.getBinDir();
                if (bin != null) prefixBin = bin.getAbsolutePath();
            } catch (Throwable ignored) {}
        }

        if (prefixBin != null) {
            return new String[] {
                    prefixBin,
                    "/system/bin",
                    "/system/xbin",
                    "/system/sbin",
                    "/sbin",
                    "/vendor/bin"
            };
        }
        return new String[] {
                "/system/bin",
                "/system/xbin",
                "/system/sbin",
                "/sbin",
                "/vendor/bin"
        };
    }

    private static boolean isSafeName(String s) {
        if (s == null || s.length() == 0 || s.length() > 64) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.';
            if (!ok) return false;
        }
        return true;
    }

    private InterpreterResolver() {}
}