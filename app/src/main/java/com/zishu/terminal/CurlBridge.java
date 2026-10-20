package com.zishu.terminal;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridges "curl ..." commands from Zishu Terminal to the built-in ZishuCurl.
 *
 * Fixes v5.0.2:
 *  - Progress lines from ZishuCurl's stderr are forwarded to the UI
 *    in real time (line-by-line), instead of being buffered until the
 *    download finishes. This makes curl show live progress.
 *
 * Preserved v5.0.1 fixes:
 *  - Expands $HOME, ${HOME}, ~, $PREFIX, $TMPDIR before args reach ZishuCurl
 *  - Resolves ALL relative file paths against the shell's actual cwd
 *  - Passes absolute paths to ZishuCurl so File APIs never use process cwd
 */
public final class CurlBridge {

    public interface Output {
        void line(String s);
    }

    public static boolean tryHandle(Context ctx, String line, String cwd, Output out) {
        if (line == null) return false;
        String trimmed = line.trim();
        if (trimmed.length() == 0) return false;

        if (!trimmed.equals("curl") && !trimmed.startsWith("curl ")) return false;

        char first = trimmed.charAt(0);
        if (first == ';' || first == '&' || first == '|') return false;

        // Parse optional redirection
        String commandPart = trimmed;
        String redirectFile = null;
        boolean append = false;

        int gtIdx = findRedirectIndex(trimmed);
        if (gtIdx > 0) {
            commandPart = trimmed.substring(0, gtIdx).trim();
            String afterGt = trimmed.substring(gtIdx + 1).trim();
            if (afterGt.startsWith(">")) {
                append = true;
                afterGt = afterGt.substring(1).trim();
            }
            if (afterGt.startsWith("\"") && afterGt.endsWith("\"") && afterGt.length() >= 2) {
                afterGt = afterGt.substring(1, afterGt.length() - 1);
            }
            redirectFile = afterGt;
        }

        List<String> tokens = tokenize(commandPart);
        if (tokens.isEmpty()) return false;
        if (!"curl".equals(tokens.get(0))) return false;

        // --- Expand environment variables in ALL tokens ---
        for (int i = 1; i < tokens.size(); i++) {
            tokens.set(i, expandEnvVars(ctx, tokens.get(i)));
        }
        if (redirectFile != null) {
            redirectFile = expandEnvVars(ctx, redirectFile);
        }

        // --- Convert relative file paths in -o / -O / --output to absolute ---
        for (int i = 1; i < tokens.size() - 1; i++) {
            String tok = tokens.get(i);
            if ("-o".equals(tok) || "--output".equals(tok)) {
                String arg = tokens.get(i + 1);
                String abs = toAbsolutePath(arg, cwd);
                tokens.set(i + 1, abs);
            }
        }

        // --- Resolve redirect target to absolute path ---
        if (redirectFile != null) {
            redirectFile = toAbsolutePath(redirectFile, cwd);
        }

        String[] args = new String[tokens.size() - 1];
        for (int i = 1; i < tokens.size(); i++) args[i - 1] = tokens.get(i);

        final String finalRedirectFile = redirectFile;
        final boolean finalAppend = append;
        final Output finalOut = out;

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runCurlInBackground(args, finalRedirectFile, finalAppend, finalOut);
            }
        });
        worker.setDaemon(true);
        worker.setName("zishu-curl");
        worker.start();

        return true;
    }

    // ---------------------------------------------------------------
    // Environment variable expansion
    // ---------------------------------------------------------------

    private static String expandEnvVars(Context ctx, String value) {
        if (value == null || value.length() == 0) return value;

        // Skip URLs
        if (value.contains("://")) return value;

        String v = value;
        try {
            ZishuEnv env = new ZishuEnv(ctx);
            String home = env.getHome().getAbsolutePath();
            String prefix = env.getPrefix().getAbsolutePath();
            String tmp = env.getTmp().getAbsolutePath();

            // ${VAR} first (longer pattern)
            v = v.replace("${HOME}", home);
            v = v.replace("${PREFIX}", prefix);
            v = v.replace("${TMPDIR}", tmp);
            // $VAR
            v = v.replace("$HOME", home);
            v = v.replace("$PREFIX", prefix);
            v = v.replace("$TMPDIR", tmp);
            // ~
            if (v.equals("~")) v = home;
            else if (v.startsWith("~/")) v = home + v.substring(1);
        } catch (Throwable ignored) {}
        return v;
    }

    /**
     * Converts a path string to an absolute path.
     * Absolute paths are returned unchanged.
     * Relative paths are resolved against `cwd`.
     * If `cwd` is null/empty, uses ZishuEnv home.
     */
    private static String toAbsolutePath(String path, String cwd) {
        if (path == null || path.length() == 0) return path;
        try {
            File f = new File(path);
            if (f.isAbsolute()) return f.getAbsolutePath();
            String base = cwd;
            if (base == null || base.length() == 0) return path;
            return new File(base, path).getAbsolutePath();
        } catch (Throwable t) {
            return path;
        }
    }

    // ---------------------------------------------------------------
    // Background execution
    // ---------------------------------------------------------------

    private static void runCurlInBackground(String[] args, String redirectFile,
                                            boolean append, Output out) {
        OutputStream stdoutStream;
        FileOutputStream fileStream = null;

        if (redirectFile != null) {
            // redirectFile is already absolute
            File target = new File(redirectFile);
            try {
                if (target.getParentFile() != null) target.getParentFile().mkdirs();
                fileStream = new FileOutputStream(target, append);
                stdoutStream = fileStream;
            } catch (Throwable t) {
                out.line("curl: (23) Failed to open output file: " + target.getAbsolutePath());
                String msg = t.getMessage();
                out.line("curl: " + (msg == null ? t.getClass().getName() : msg));
                return;
            }
        } else {
            stdoutStream = new LineWriter(out);
        }

        // stderr forwarded in real time (line by line) to the terminal.
        OutputStream stderrStream = new LineWriter(out);

        int exit;
        try {
            exit = ZishuCurl.run(args, stdoutStream, stderrStream);
        } catch (Throwable t) {
            String msg = t.getMessage();
            String cls = t.getClass().getName();
            if (msg == null || msg.length() == 0) {
                out.line("curl: bridge error: " + cls);
            } else {
                out.line("curl: bridge error: " + cls + ": " + msg);
            }
            exit = 7;
        }

        if (fileStream != null) {
            try { fileStream.flush(); fileStream.close(); } catch (Throwable ignored) {}
        } else if (stdoutStream instanceof LineWriter) {
            ((LineWriter) stdoutStream).flush();
        }

        // Flush any remaining stderr text
        if (stderrStream instanceof LineWriter) {
            ((LineWriter) stderrStream).flush();
        }

        if (exit != 0) {
            out.line("[curl exited with code " + exit + "]");
        }
    }

    // ---------------------------------------------------------------
    // Parsing helpers
    // ---------------------------------------------------------------

    private static int findRedirectIndex(String s) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) { i++; continue; }
            if (c == '\'' && !inDouble) { inSingle = !inSingle; continue; }
            if (c == '"' && !inSingle) { inDouble = !inDouble; continue; }
            if (c == '>' && !inSingle && !inDouble) return i;
        }
        return -1;
    }

    private static List<String> tokenize(String s) {
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean any = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && !inSingle) {
                cur.append(s.charAt(++i));
                any = true;
                continue;
            }
            if (c == '\'' && !inDouble) { inSingle = !inSingle; any = true; continue; }
            if (c == '"' && !inSingle) { inDouble = !inDouble; any = true; continue; }
            if ((c == ' ' || c == '\t') && !inSingle && !inDouble) {
                if (any) { out.add(cur.toString()); cur.setLength(0); any = false; }
                continue;
            }
            cur.append(c);
            any = true;
        }
        if (any) out.add(cur.toString());
        return out;
    }

    /**
     * Emits data to the terminal one full line at a time.
     * Used for both stdout (when not redirected) and stderr (always).
     * This makes curl progress update in real time instead of buffering
     * until the download finishes.
     */
    private static class LineWriter extends OutputStream {
        private final Output out;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private final Handler mainHandler = new Handler(Looper.getMainLooper());

        LineWriter(Output o) { this.out = o; }

        @Override public void write(int b) { buf.write(b); }

        @Override public void write(byte[] b) throws java.io.IOException {
            write(b, 0, b.length);
        }

        @Override public void write(byte[] b, int off, int len) {
            for (int i = off; i < off + len; i++) {
                byte v = b[i];
                if (v == '\n') emit();
                else if (v != '\r') buf.write(v);
            }
        }

        @Override public void flush() { emit(); }
        @Override public void close() { emit(); }

        private void emit() {
            if (buf.size() == 0) return;
            final String line;
            try { line = buf.toString("UTF-8"); }
            catch (Throwable t) { buf.reset(); return; }
            buf.reset();
            mainHandler.post(new Runnable() {
                @Override public void run() { out.line(line); }
            });
        }
    }

    private CurlBridge() {}
}
