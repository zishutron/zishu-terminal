package com.zishu.terminal;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.SSLException;

/**
 * Real Java-based curl implementation for Zishu Terminal.
 *
 * v5.0.3 — Human-readable progress display.
 *
 * Progress behavior:
 *  - If the server sends Content-Length, we show percentage progress
 *    with human-readable sizes (B / KB / MB / GB).
 *  - If not, we show periodic "Received: N" updates.
 *  - A new progress line is printed at most once per second
 *    (PROGRESS_INTERVAL_MS).
 *  - If no progress line has been printed for PROGRESS_STALE_MS,
 *    force one so the user knows curl is alive.
 *
 * Progress lines are written to stderr. When stdout is redirected to a
 * file (e.g. "curl URL > file.html"), only the response body is saved
 * into the file — progress stays on the terminal.
 */
public final class ZishuCurl {

    public static final String VERSION = "ZishuCurl 1.3 (Android HttpURLConnection)";

    /** Minimum time between two progress lines (milliseconds). */
    private static final long PROGRESS_INTERVAL_MS = 1000L;

    /** Force a progress line after this much idle time. */
    private static final long PROGRESS_STALE_MS = 5000L;

    public static void main(String[] args) {
        int exit = run(args, System.out, System.err);
        System.exit(exit);
    }

    public static int run(String[] args, OutputStream stdout, OutputStream stderr) {
        Options opts = new Options();

        try {
            parse(args, opts);
        } catch (HelpRequested h) {
            printHelp(stdout);
            return 0;
        } catch (VersionRequested v) {
            printVersion(stdout);
            return 0;
        } catch (ArgError e) {
            println(stderr, "curl: " + e.getMessage());
            return 2;
        }

        if (opts.url == null) {
            println(stderr, "curl: no URL specified");
            println(stderr, "Try 'curl --help' for more information.");
            return 2;
        }

        return execute(opts, stdout, stderr);
    }

    // ---------------------------------------------------------------
    // Options
    // ---------------------------------------------------------------

    private static class Options {
        String url;
        String method;
        String outputFile;
        boolean remoteName;
        boolean headOnly;
        boolean followRedirects;
        boolean silent;
        boolean verbose;
        boolean showHeaders;
        int connectTimeoutSec = 30;
        int readTimeoutSec = 60;
        String postData;
        List<String> headers = new ArrayList<String>();
    }

    private static class HelpRequested extends Exception {}
    private static class VersionRequested extends Exception {}
    private static class ArgError extends Exception {
        ArgError(String m) { super(m); }
    }

    private static void parse(String[] args, Options o)
            throws HelpRequested, VersionRequested, ArgError {
        if (args == null || args.length == 0) throw new HelpRequested();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a == null) continue;

            if (a.equals("-h") || a.equals("--help")) throw new HelpRequested();
            if (a.equals("--version") || a.equals("-V")) throw new VersionRequested();

            if (a.equals("-o")) {
                if (i + 1 >= args.length) throw new ArgError("-o requires an argument");
                o.outputFile = args[++i];
            } else if (a.equals("-O") || a.equals("--remote-name")) {
                o.remoteName = true;
            } else if (a.equals("-I") || a.equals("--head")) {
                o.headOnly = true;
                if (o.method == null) o.method = "HEAD";
            } else if (a.equals("-L") || a.equals("--location")) {
                o.followRedirects = true;
            } else if (a.equals("-s") || a.equals("--silent")) {
                o.silent = true;
            } else if (a.equals("-v") || a.equals("--verbose")) {
                o.verbose = true;
            } else if (a.equals("-i") || a.equals("--include")) {
                o.showHeaders = true;
            } else if (a.equals("--no-progress")) {
                o.silent = true;
            } else if (a.equals("-k") || a.equals("--insecure")) {
                throw new ArgError("-k/--insecure is disabled: certificate verification is always required");
            } else if (a.equals("-X") || a.equals("--request")) {
                if (i + 1 >= args.length) throw new ArgError("-X requires an argument");
                o.method = args[++i].toUpperCase(Locale.US);
            } else if (a.equals("-H") || a.equals("--header")) {
                if (i + 1 >= args.length) throw new ArgError("-H requires an argument");
                o.headers.add(args[++i]);
            } else if (a.equals("-d") || a.equals("--data")) {
                if (i + 1 >= args.length) throw new ArgError("-d requires an argument");
                o.postData = args[++i];
            } else if (a.equals("--connect-timeout")) {
                if (i + 1 >= args.length) throw new ArgError("--connect-timeout requires an argument");
                try { o.connectTimeoutSec = Integer.parseInt(args[++i]); }
                catch (Throwable t) { throw new ArgError("invalid --connect-timeout value"); }
            } else if (a.equals("--max-time")) {
                if (i + 1 >= args.length) throw new ArgError("--max-time requires an argument");
                try { o.readTimeoutSec = Integer.parseInt(args[++i]); }
                catch (Throwable t) { throw new ArgError("invalid --max-time value"); }
            } else if (a.startsWith("-") && !a.equals("-")) {
                throw new ArgError("unknown option: " + a);
            } else {
                if (o.url == null) o.url = a;
                else throw new ArgError("multiple URLs not supported");
            }
        }
    }

    // ---------------------------------------------------------------
    // Execute
    // ---------------------------------------------------------------

    private static int execute(Options o, OutputStream stdout, OutputStream stderr) {
        HttpURLConnection conn = null;
        try {
            String urlStr = o.url;
            if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
                urlStr = "https://" + urlStr;
            }

            URL url;
            try {
                url = new URL(urlStr);
            } catch (Throwable t) {
                println(stderr, "curl: (3) URL malformed: " + o.url);
                return 3;
            }

            if (o.verbose) {
                println(stderr, "* Trying " + url.getHost() + "...");
            }

            conn = (HttpURLConnection) url.openConnection();

            // Force TLS 1.2 for HTTPS
            try {
                if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                    javax.net.ssl.SSLContext sc =
                            javax.net.ssl.SSLContext.getInstance("TLSv1.2");
                    sc.init(null, null, null);
                    ((javax.net.ssl.HttpsURLConnection) conn)
                            .setSSLSocketFactory(sc.getSocketFactory());
                }
            } catch (Throwable ignored) {}

            String method = o.method;
            if (method == null) method = (o.postData != null) ? "POST" : "GET";
            conn.setRequestMethod(method);

            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(Math.max(1, o.connectTimeoutSec) * 1000);
            conn.setReadTimeout(Math.max(1, o.readTimeoutSec) * 1000);

            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Connection", "close");

            for (int i = 0; i < o.headers.size(); i++) {
                String h = o.headers.get(i);
                if (h == null) continue;
                int c = h.indexOf(':');
                if (c > 0) {
                    String k = h.substring(0, c).trim();
                    String v = h.substring(c + 1).trim();
                    conn.setRequestProperty(k, v);
                }
            }

            if (o.postData != null) {
                conn.setDoOutput(true);
                byte[] body = o.postData.getBytes("UTF-8");
                conn.setRequestProperty("Content-Length", String.valueOf(body.length));
                if (conn.getRequestProperty("Content-Type") == null) {
                    conn.setRequestProperty("Content-Type",
                            "application/x-www-form-urlencoded");
                }
                OutputStream os = conn.getOutputStream();
                os.write(body);
                os.flush();
                try { os.close(); } catch (Throwable ignored) {}
            }

            long startTime = System.currentTimeMillis();

            int status;
            try {
                status = conn.getResponseCode();
            } catch (SSLException ssl) {
                String msg = ssl.getMessage() == null ? "SSL error" : ssl.getMessage();
                String lower = msg.toLowerCase(Locale.US);
                if (lower.contains("certificate") || lower.contains("trust")) {
                    println(stderr, "curl: (60) SSL certificate problem: " + msg);
                    return 60;
                }
                println(stderr, "curl: (35) SSL connect error: " + msg);
                return 35;
            } catch (java.net.UnknownHostException uh) {
                println(stderr, "curl: (6) Could not resolve host: " + url.getHost());
                return 6;
            } catch (java.net.SocketTimeoutException st) {
                println(stderr, "curl: (28) Operation timed out");
                return 28;
            } catch (java.net.ConnectException ce) {
                println(stderr, "curl: (7) Failed to connect: " + url.getHost());
                return 7;
            } catch (IOException io) {
                println(stderr, "curl: (7) Failed to connect: " + io.getMessage());
                return 7;
            }

            if (o.verbose) {
                println(stderr, "< HTTP/1.1 " + status + " " + conn.getResponseMessage());
            }

            long contentLength = -1;
            try {
                contentLength = conn.getContentLengthLong();
            } catch (Throwable ignored) {
                contentLength = conn.getContentLength();
            }

            OutputStream out = stdout;
            FileOutputStream fout = null;
            if (o.outputFile != null || o.remoteName) {
                String name = o.outputFile != null ? o.outputFile : filenameFromUrl(url);
                try {
                    fout = new FileOutputStream(name);
                    out = fout;
                } catch (Throwable t) {
                    println(stderr, "curl: (23) Failed to open output file: " + name);
                    return 23;
                }
            }

            // Header display for -I / -i
            if (o.headOnly || o.showHeaders) {
                StringBuilder sb = new StringBuilder();
                String msg = conn.getResponseMessage();
                sb.append("HTTP/1.1 ").append(status).append(' ')
                  .append(msg == null ? "" : msg).append("\r\n");
                java.util.Map<String, java.util.List<String>> hdrs = conn.getHeaderFields();
                for (java.util.Map.Entry<String, java.util.List<String>> e : hdrs.entrySet()) {
                    String k = e.getKey();
                    if (k == null) continue;
                    List<String> vs = e.getValue();
                    for (int i = 0; i < vs.size(); i++) {
                        sb.append(k).append(": ").append(vs.get(i)).append("\r\n");
                    }
                }
                sb.append("\r\n");
                out.write(sb.toString().getBytes("UTF-8"));
                out.flush();
            }

            // ---- Body with progress ----
            long totalBytesRead = 0;
            if (!o.headOnly) {
                InputStream in = null;
                try {
                    if (status >= 400) in = conn.getErrorStream();
                    else in = conn.getInputStream();
                } catch (Throwable t) {
                    in = null;
                }

                if (in != null) {
                    boolean showProgress = !o.silent;
                    boolean knownSize = contentLength > 0;

                    // Progress header (once)
                    if (showProgress && knownSize) {
                        println(stderr, "");
                        println(stderr, "  %      Downloaded       Total          Speed");
                        println(stderr, "  --     ----------       -----          -----");
                        printPercentLine(stderr, 0, contentLength, 0, startTime);
                    }

                    int lastPercentPrinted = -1;
                    long lastTimePrinted = System.currentTimeMillis();

                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        totalBytesRead += n;

                        if (showProgress) {
                            long now = System.currentTimeMillis();
                            long timeSince = now - lastTimePrinted;

                            if (knownSize) {
                                int pct = (int) ((totalBytesRead * 100) / contentLength);

                                boolean timeReady = timeSince >= PROGRESS_INTERVAL_MS;
                                boolean percentChanged = (pct != lastPercentPrinted);
                                boolean stale = timeSince >= PROGRESS_STALE_MS;

                                if ((timeReady && percentChanged) || stale) {
                                    printPercentLine(stderr, pct, contentLength,
                                            totalBytesRead, startTime);
                                    lastPercentPrinted = pct;
                                    lastTimePrinted = now;
                                }
                            } else {
                                if (timeSince >= PROGRESS_INTERVAL_MS) {
                                    printReceivedLine(stderr, totalBytesRead, startTime);
                                    lastTimePrinted = now;
                                }
                            }
                        }
                    }
                    out.flush();

                    // Final progress line
                    if (showProgress) {
                        if (knownSize) {
                            printPercentLine(stderr, 100, contentLength,
                                    contentLength, startTime);
                        } else {
                            long elapsed = Math.max(1,
                                    System.currentTimeMillis() - startTime);
                            long speed = (totalBytesRead * 1000L) / elapsed;
                            println(stderr, "");
                            println(stderr, "  Received: " + humanBytes(totalBytesRead)
                                    + " in " + formatDuration(elapsed)
                                    + " (" + humanBytes(speed) + "/s)");
                        }
                    }

                    try { in.close(); } catch (Throwable ignored) {}
                }
            }

            if (fout != null) {
                try { fout.flush(); fout.close(); } catch (Throwable ignored) {}
            }

            if (status >= 400) {
                if (o.verbose) println(stderr, "* HTTP error " + status);
                return 22;
            }

            return 0;

        } catch (Throwable t) {
            String msg = t.getMessage();
            String cls = t.getClass().getName();
            if (msg == null || msg.length() == 0) {
                println(stderr, "curl: unexpected error: " + cls);
            } else {
                println(stderr, "curl: unexpected error: " + cls + ": " + msg);
            }
            return 7;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Human-readable progress line (total size known).
     *
     * Example:
     *   "   45%   45.2 MB / 100.7 MB   @  52.1 KB/s"
     */
    private static void printPercentLine(OutputStream stderr,
                                         int percent,
                                         long total,
                                         long received,
                                         long startTime) {
        long elapsed = Math.max(1, System.currentTimeMillis() - startTime);
        long speed = (received * 1000L) / elapsed;
        String line = String.format(Locale.US,
                "  %3d%%   %-14s   %-14s   %s/s",
                percent,
                humanBytes(received),
                humanBytes(total),
                humanBytes(speed));
        println(stderr, line);
    }

    /**
     * Periodic "Received" line when total size is unknown.
     */
    private static void printReceivedLine(OutputStream stderr,
                                          long received,
                                          long startTime) {
        long elapsed = Math.max(1, System.currentTimeMillis() - startTime);
        long speed = (received * 1000L) / elapsed;
        println(stderr, "  " + humanBytes(received) + " received   ("
                + humanBytes(speed) + "/s)");
    }

    private static String humanBytes(long b) {
        if (b < 0) return "?";
        if (b < 1024) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    /** "3.5s" or "2m 15s" */
    private static String formatDuration(long ms) {
        long totalSec = ms / 1000L;
        if (totalSec < 60) {
            return String.format(Locale.US, "%.1fs", ms / 1000.0);
        }
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return min + "m " + sec + "s";
    }

    private static String filenameFromUrl(URL url) {
        String path = url.getPath();
        if (path == null || path.length() == 0 || path.equals("/")) return "index.html";
        int slash = path.lastIndexOf('/');
        String name = (slash >= 0) ? path.substring(slash + 1) : path;
        if (name.length() == 0) name = "index.html";
        return name;
    }

    // ---------------------------------------------------------------
    // Help / version
    // ---------------------------------------------------------------

    private static void printVersion(OutputStream out) {
        println(out, VERSION);
        println(out, "Protocols: http, https");
        println(out, "Features: TLS, redirects, custom headers, POST, progress");
        println(out, "Built into Zishu Terminal");
    }

    private static void printHelp(OutputStream out) {
        println(out, "Usage: curl [options] URL");
        println(out, "");
        println(out, "Common options:");
        println(out, "  -o FILE         Write output to FILE");
        println(out, "  -O              Write output to file named from URL");
        println(out, "  -I              HEAD request (print headers only)");
        println(out, "  -i              Include response headers in output");
        println(out, "  -L              Follow redirects (default: on)");
        println(out, "  -s              Silent mode (no progress)");
        println(out, "  -v              Verbose mode");
        println(out, "  -X METHOD       HTTP method (GET, POST, PUT, DELETE, HEAD)");
        println(out, "  -H \"K: V\"       Add custom header (repeatable)");
        println(out, "  -d DATA         POST body data");
        println(out, "  --connect-timeout N   Connection timeout (seconds)");
        println(out, "  --max-time N          Total timeout (seconds)");
        println(out, "  --no-progress   Suppress progress lines");
        println(out, "  -h, --help      Show this help");
        println(out, "  --version       Show version");
        println(out, "");
        println(out, "Examples:");
        println(out, "  curl https://example.com");
        println(out, "  curl https://example.com > source.html");
        println(out, "  curl -o page.html https://example.com");
        println(out, "  curl -O https://example.com/file.txt");
        println(out, "  curl -I https://example.com");
        println(out, "  curl -L https://example.com");
        println(out, "  curl -s https://example.com");
        println(out, "");
        println(out, "Notes:");
        println(out, "  - Certificate verification is always enabled.");
        println(out, "  - Progress lines go to stderr; redirecting stdout to a");
        println(out, "    file will not mix progress into the saved content.");
        println(out, "  - Progress updates are printed at most once per second.");
        println(out, "  - Sizes are shown in B / KB / MB / GB.");
    }

    private static void println(OutputStream out, String s) {
        if (out == null) return;
        try {
            out.write((s + "\n").getBytes("UTF-8"));
            out.flush();
        } catch (Throwable ignored) {}
    }

    private ZishuCurl() {}
}
