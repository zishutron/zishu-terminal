package com.zishu.terminal;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages a persistent /system/bin/sh process.
 *
 * v5.0.1: Switched from interactive (-i) shell to non-interactive mode
 *         to prevent the shell prompt from leaking into command output.
 * v5.0.2: Replaced BufferedReader.readLine() with byte-level streaming
 *         so binary output (e.g. `cat file.zenc`) is flushed immediately
 *         instead of waiting for a newline. Uses a short idle timeout to
 *         emit partial lines / binary data, and preserves ANSI colors,
 *         UTF-8 text, and prompt filtering.
 */
public class ShellProcess {

    public interface Listener {
        void onOutput(String line);
        void onError(String line);
        void onExit(int code);
    }

    /** If no new data arrives within this window, flush what we have. */
    private static final long IDLE_FLUSH_MS = 100L;

    /** If the pending buffer grows beyond this, flush it (binary data). */
    private static final int MAX_PENDING_BYTES = 8 * 1024;

    /** Read buffer size. */
    private static final int READ_BUF = 4096;

    /** How long the reader sleeps between availability checks. */
    private static final long POLL_SLEEP_MS = 15L;

    private Process process;
    private OutputStreamWriter stdin;
    private Thread stdoutThread;
    private Thread stderrThread;
    private Thread waiterThread;
    private volatile boolean running;
    private Listener listener;
    private String cwd;

    public ShellProcess(String cwd, Listener listener) {
        this.cwd = cwd;
        this.listener = listener;
    }

    public synchronized boolean isRunning() {
        return running && process != null;
    }

    public synchronized boolean start() {
        if (isRunning()) return true;
        try {
            // Non-interactive shell — no -i flag.
            // This prevents the shell from printing prompts to stderr.
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh");

            if (cwd != null) {
                File dir = new File(cwd);
                if (!dir.isDirectory()) {
                    try { dir.mkdirs(); } catch (Throwable ignored) {}
                }
                if (dir.isDirectory()) {
                    pb.directory(dir);
                }
            }

            // Keep stderr separate so we can suppress prompts / warnings.
            pb.redirectErrorStream(false);

            // Disable prompt
            pb.environment().put("PS1", "");
            pb.environment().put("PS2", "");
            pb.environment().put("ENV", "");

            process = pb.start();

            stdin = new OutputStreamWriter(process.getOutputStream());
            running = true;

            stdoutThread = new Thread(new StreamReader(process.getInputStream(), true));
            stdoutThread.setDaemon(true);
            stdoutThread.setName("zishu-stdout");
            stdoutThread.start();

            stderrThread = new Thread(new StreamReader(process.getErrorStream(), false));
            stderrThread.setDaemon(true);
            stderrThread.setName("zishu-stderr");
            stderrThread.start();

            waiterThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    int code = -1;
                    try { code = process.waitFor(); }
                    catch (Throwable t) { code = -1; }
                    running = false;
                    if (listener != null) listener.onExit(code);
                }
            });
            waiterThread.setDaemon(true);
            waiterThread.setName("zishu-waiter");
            waiterThread.start();

            // Send startup commands to disable prompt and CD to cwd
            try {
                if (cwd != null) {
                    write("cd \"" + cwd + "\"");
                }
                write("PS1=''");
                write("PS2=''");
            } catch (Throwable ignored) {}

            return true;
        } catch (Throwable t) {
            running = false;
            if (listener != null) {
                listener.onError("Shell unavailable: " + t.getMessage());
            }
            return false;
        }
    }

    public void write(String cmd) {
        try {
            if (stdin != null) {
                stdin.write(cmd);
                if (cmd != null && !cmd.endsWith("\n")) {
                    stdin.write("\n");
                }
                stdin.flush();
            }
        } catch (Throwable t) {
            if (listener != null) {
                listener.onError("Shell write failed: " + t.getMessage());
            }
        }
    }

    public void cdTo(String path) {
        this.cwd = path;
    }

    public String getCwd() {
        return cwd;
    }

    public synchronized void stop() {
        running = false;
        try { if (stdin != null) stdin.close(); } catch (Throwable ignored) {}
        try { if (process != null) process.destroy(); } catch (Throwable ignored) {}
        process = null;
        stdin = null;
    }

    // ---------------------------------------------------------------
    // StreamReader — byte-level, low-latency, line + binary aware
    // ---------------------------------------------------------------

    private class StreamReader implements Runnable {
        private final InputStream in;
        private final boolean stdout;

        StreamReader(InputStream in, boolean stdout) {
            this.in = in;
            this.stdout = stdout;
        }

        @Override
        public void run() {
            // Accumulator for bytes that have not yet been emitted.
            ByteArrayOutputStream pending = new ByteArrayOutputStream(READ_BUF);
            byte[] readBuf = new byte[READ_BUF];
            long lastDataAt = System.currentTimeMillis();

            try {
                while (running) {
                    int avail = in.available();
                    if (avail > 0) {
                        int toRead = Math.min(readBuf.length, avail);
                        int n = in.read(readBuf, 0, toRead);
                        if (n < 0) break; // EOF
                        if (n > 0) {
                            pending.write(readBuf, 0, n);
                            lastDataAt = System.currentTimeMillis();
                            // 1) Emit all complete lines
                            flushCompleteLines(pending);
                            // 2) If pending grew too large (binary), emit it
                            if (pending.size() >= MAX_PENDING_BYTES) {
                                emitAndClear(pending);
                            }
                        }
                    } else {
                        // No data available right now.
                        // If we've been idle for a while and have pending
                        // bytes, flush them. This is what fixes `cat` on
                        // binary files (no newline → would otherwise wait
                        // for the next command's newline).
                        long now = System.currentTimeMillis();
                        if (pending.size() > 0
                                && (now - lastDataAt) >= IDLE_FLUSH_MS) {
                            emitAndClear(pending);
                            lastDataAt = now;
                        }
                        // Small sleep to avoid busy-looping.
                        try { Thread.sleep(POLL_SLEEP_MS); }
                        catch (InterruptedException ie) { /* ignore */ }
                    }
                }

                // Process exited — flush whatever remains.
                if (pending.size() > 0) {
                    emitAndClear(pending);
                }
            } catch (Throwable t) {
                // Silent — do not crash the reader thread.
            } finally {
                try { in.close(); } catch (IOException ignored) {}
            }
        }

        /**
         * Emits all complete lines from `pending` (i.e. everything up to
         * and including the last '\n'). Keeps any trailing partial line
         * in the buffer.
         */
        private void flushCompleteLines(ByteArrayOutputStream pending) {
            byte[] data = pending.toByteArray();
            int lastNL = -1;
            for (int i = data.length - 1; i >= 0; i--) {
                if (data[i] == '\n') { lastNL = i; break; }
            }
            if (lastNL < 0) return;

            // Emit bytes [0 .. lastNL] as one or more lines.
            emitBytes(data, 0, lastNL + 1);

            // Keep remainder
            pending.reset();
            if (lastNL + 1 < data.length) {
                pending.write(data, lastNL + 1, data.length - lastNL - 1);
            }
        }

        /**
         * Emits the entire pending buffer and clears it. Used for binary
         * data or idle timeouts.
         */
        private void emitAndClear(ByteArrayOutputStream pending) {
            byte[] data = pending.toByteArray();
            if (data.length == 0) return;
            emitBytes(data, 0, data.length);
            pending.reset();
        }

        /**
         * Converts raw bytes to a UTF-8 String, splits on '\n', applies
         * prompt/error filtering, and dispatches each line to the listener.
         *
         * - Trailing '\r' (from CRLF) is stripped.
         * - Invalid UTF-8 sequences are replaced with U+FFFD by the
         *   String constructor, which is what we want for binary data:
         *   the garbage still reaches the UI, and the terminal's ANSI
         *   parser will render printable characters as-is.
         */
        private void emitBytes(byte[] data, int off, int len) {
            if (len <= 0) return;

            String text;
            try {
                text = new String(data, off, len, "UTF-8");
            } catch (Throwable t) {
                // Fallback — should not happen for UTF-8, but be safe.
                text = new String(data, off, len);
            }

            // Split into lines, keeping empty trailing entries.
            // We only split on '\n'; lone '\r' is stripped later.
            List<String> lines = new ArrayList<String>();
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    lines.add(text.substring(start, i));
                    start = i + 1;
                }
            }
            // Trailing chunk (no newline) — this happens for binary
            // data emitted on idle. Treat it as a single "line".
            if (start < text.length()) {
                lines.add(text.substring(start));
            }

            if (listener == null) return;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                // Strip trailing CR
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }

                // ---- Existing prompt / warning filters ----
                if (line.trim().endsWith("$") && line.contains(":/")) {
                    continue;
                }
                if (line.startsWith("/system/bin/sh: can't find tty")) {
                    continue;
                }
                if (line.startsWith("/system/bin/sh: warning:")) {
                    continue;
                }

                if (stdout) listener.onOutput(line);
                else listener.onError(line);
            }
        }
    }
}
