package com.zishu.terminal;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ZgitClone - Java-based GitHub repository cloner.
 *
 * Uses GitHub's codeload ZIP endpoint (official, public API):
 *   https://codeload.github.com/{user}/{repo}/zip/refs/heads/{branch}
 *
 * Supports:
 *   zgit clone <url>            - clone to ./<repo>
 *   zgit clone <url> <dir>      - clone to <dir>
 *   zgit help
 *   zgit version
 *
 * Does NOT support:
 *   - git pull / push / fetch
 *   - private repositories (no auth)
 *   - git history (only latest snapshot)
 *
 * Traditional Java 6/7 syntax. No lambdas. No AndroidX.
 */
public class ZgitClone {

    public static final String VERSION = "1.0.2";

    // Max download size: 200 MB (safety)
    private static final long MAX_DOWNLOAD_BYTES = 200L * 1024L * 1024L;

    // Max single extracted file: 50 MB
    private static final long MAX_ENTRY_BYTES = 50L * 1024L * 1024L;

    // Max total extracted size: 500 MB
    private static final long MAX_TOTAL_BYTES = 500L * 1024L * 1024L;

    private final LineWriter out;
    private final File cwd;
    private final ZishuEnv env;

    /**
     * LineWriter is a small interface that the caller implements to
     * push output back to the terminal on the main thread.
     */
    public interface LineWriter {
        void writeLine(String line);
    }

    public ZgitClone(LineWriter out, File cwd, ZishuEnv env) {
        this.out = out;
        this.cwd = cwd;
        this.env = env;
    }

    /**
     * Entry point. Returns true if the command was handled (even if it
     * failed), false if the command is not a zgit command.
     */
    public boolean tryHandle(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        if (!trimmed.equals("zgit") && !trimmed.startsWith("zgit ")) {
            return false;
        }

        String[] parts = splitArgs(trimmed);
        // parts[0] = "zgit"
        if (parts.length < 2) {
            printHelp();
            return true;
        }

        String sub = parts[1];
        if ("help".equals(sub) || "--help".equals(sub) || "-h".equals(sub)) {
            printHelp();
            return true;
        }
        if ("version".equals(sub) || "--version".equals(sub) || "-v".equals(sub)) {
            out.writeLine("zgit " + VERSION + " (Java-based, codeload ZIP method)");
            return true;
        }
        if ("clone".equals(sub)) {
            if (parts.length < 3) {
                out.writeLine("zgit: error: clone needs a URL");
                out.writeLine("usage: zgit clone <github-url> [target-dir]");
                return true;
            }
            String url = parts[2];
            String targetDir = null;
            if (parts.length >= 4) {
                targetDir = parts[3];
            }
            doClone(url, targetDir);
            return true;
        }

        out.writeLine("zgit: unknown subcommand: " + sub);
        out.writeLine("run 'zgit help'");
        return true;
    }

    // ------------------------------------------------------------------
    // Clone implementation
    // ------------------------------------------------------------------

    private void doClone(final String rawUrl, final String targetDirArg) {
        // Parse URL first (fast, no network)
        final RepoRef ref;
        try {
            ref = parseGithubUrl(rawUrl);
        } catch (IllegalArgumentException e) {
            out.writeLine("zgit: " + e.getMessage());
            out.writeLine("supported: https://github.com/<user>/<repo>");
            return;
        }

        final File targetDir;
        if (targetDirArg != null) {
            File tmp = new File(targetDirArg);
            if (!tmp.isAbsolute()) {
                tmp = new File(cwd, targetDirArg);
            }
            targetDir = tmp;
        } else {
            targetDir = new File(cwd, ref.repo);
        }

        if (targetDir.exists()) {
            out.writeLine("zgit: error: target already exists: " + targetDir.getAbsolutePath());
            out.writeLine("remove it first, or pass a different target directory");
            return;
        }

        out.writeLine("Cloning into '" + targetDir.getName() + "'...");
        out.writeLine("  repo:   " + ref.user + "/" + ref.repo);
        out.writeLine("  branch: " + ref.branch);

        // Network on background thread
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                File tempZip = null;
                try {
                    File tmpDir = new File(cwd, ".zgit-tmp");
                    if (!tmpDir.exists() && !tmpDir.mkdirs()) {
                        writeMain("zgit: error: cannot create temp dir: " + tmpDir.getAbsolutePath());
                        return;
                    }

                    // Try requested branch first, then fall back to the other
                    // common default (main <-> master). GitHub uses both.
                    String[] tryBranches = buildBranchCandidates(ref.branch);
                    long bytes = -1;
                    String usedBranch = null;
                    for (int bi = 0; bi < tryBranches.length; bi++) {
                        String br = tryBranches[bi];
                        String zipUrl = "https://codeload.github.com/"
                                + ref.user + "/" + ref.repo
                                + "/zip/refs/heads/" + br;
                        File candidateZip = new File(tmpDir, ref.repo + "-" + br + ".zip");

                        boolean isLast = (bi == tryBranches.length - 1);
                        writeMain("  fetching: " + zipUrl);
                        long got = downloadToFile(zipUrl, candidateZip, !isLast);
                        if (got >= 0) {
                            bytes = got;
                            usedBranch = br;
                            tempZip = candidateZip;
                            break;
                        }
                        // clean partial file
                        if (candidateZip.exists()) candidateZip.delete();

                        if (!isLast) {
                            writeMain("  branch '" + br + "' not found, trying '"
                                    + tryBranches[bi + 1] + "'...");
                        }
                    }

                    if (bytes < 0 || usedBranch == null) {
                        writeMain("zgit: error: repository not found on any known branch");
                        writeMain("      tried: " + joinBranches(tryBranches));
                        return;
                    }

                    // update ref so later messages use the right branch
                    ref.branch = usedBranch;
                    writeMain("  downloaded: " + formatSize(bytes)
                            + " (branch: " + usedBranch + ")");

                    // Extract
                    writeMain("  extracting...");
                    int fileCount = extractZipSafe(tempZip, targetDir);
                    writeMain("  extracted: " + fileCount + " files");

                    // Flatten the "repo-branch/" top dir into targetDir
                    boolean flattened = flattenTopDir(targetDir);

                    // Report
                    writeMain("");
                    writeMain("Done. Repository cloned to:");
                    writeMain("  " + targetDir.getAbsolutePath());
                    writeMain("");
                    writeMain("Note: this is a snapshot, not a full git repo.");
                    writeMain("      'git pull' / 'git push' are not supported.");

                } catch (IOException e) {
                    writeMain("zgit: error: " + e.getMessage());
                } finally {
                    if (tempZip != null && tempZip.exists()) {
                        tempZip.delete();
                    }
                    // clean up temp dir if empty
                    File tmpDir = new File(cwd, ".zgit-tmp");
                    if (tmpDir.exists()) {
                        File[] kids = tmpDir.listFiles();
                        if (kids == null || kids.length == 0) {
                            tmpDir.delete();
                        }
                    }
                }
            }
        }, "zgit-clone");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Returns branch candidates to try in order.
     * If user explicitly asked for a specific branch (not main/master),
     * only that one is tried. Otherwise, main and master are both tried.
     */
    private String[] buildBranchCandidates(String requested) {
        if (requested == null || requested.length() == 0) {
            return new String[] { "main", "master" };
        }
        if ("main".equals(requested)) {
            return new String[] { "main", "master" };
        }
        if ("master".equals(requested)) {
            return new String[] { "master", "main" };
        }
        // user asked for a specific branch: don't fall back
        return new String[] { requested };
    }

    private String joinBranches(String[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    /**
     * Parse a GitHub URL into user / repo / branch.
     * Accepted forms:
     *   https://github.com/user/repo
     *   https://github.com/user/repo.git
     *   https://github.com/user/repo/tree/branch
     *   git@github.com:user/repo.git   (converted)
     *
     * Default branch is "main" (fallback to "master" attempted at download).
     */
    private RepoRef parseGithubUrl(String raw) {
        if (raw == null) throw new IllegalArgumentException("null URL");
        String url = raw.trim();

        // Convert SSH form to HTTPS form
        if (url.startsWith("git@github.com:")) {
            url = "https://github.com/" + url.substring("git@github.com:".length());
        }

        // Strip .git suffix
        if (url.endsWith(".git")) {
            url = url.substring(0, url.length() - 4);
        }

        String prefix = "https://github.com/";
        String prefixNoScheme = "github.com/";
        if (url.startsWith(prefix)) {
            url = url.substring(prefix.length());
        } else if (url.startsWith(prefixNoScheme)) {
            url = url.substring(prefixNoScheme.length());
        } else if (url.startsWith("http://github.com/")) {
            url = url.substring("http://github.com/".length());
        } else {
            throw new IllegalArgumentException("not a GitHub URL: " + raw);
        }

        String branch = "main";
        // Check for /tree/<branch>
        int treeIdx = url.indexOf("/tree/");
        if (treeIdx >= 0) {
            branch = url.substring(treeIdx + "/tree/".length());
            // strip trailing slash
            if (branch.endsWith("/")) {
                branch = branch.substring(0, branch.length() - 1);
            }
            url = url.substring(0, treeIdx);
        }

        String[] parts = url.split("/");
        List<String> clean = new ArrayList<String>();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p != null && p.length() > 0) {
                clean.add(p);
            }
        }
        if (clean.size() < 2) {
            throw new IllegalArgumentException("URL must be github.com/<user>/<repo>");
        }

        RepoRef r = new RepoRef();
        r.user = clean.get(0);
        r.repo = clean.get(1);
        r.branch = branch;

        // validate name chars (basic)
        if (!isSafeName(r.user) || !isSafeName(r.repo)) {
            throw new IllegalArgumentException("invalid characters in user/repo name");
        }
        if (!isSafeName(r.branch)) {
            throw new IllegalArgumentException("invalid characters in branch name");
        }
        return r;
    }

    private boolean isSafeName(String s) {
        if (s == null || s.length() == 0) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '/';
            if (!ok) return false;
        }
        // no ".." or leading slash
        if (s.indexOf("..") >= 0) return false;
        if (s.startsWith("/")) return false;
        return true;
    }

    // ------------------------------------------------------------------
    // HTTP download
    // ------------------------------------------------------------------

    /**
     * Download URL to file. Returns bytes written, or -1 on error.
     * The 404 message is only printed when quietOn404 is false.
     */
    private long downloadToFile(String urlStr, File dest) {
        return downloadToFile(urlStr, dest, false);
    }

    private long downloadToFile(String urlStr, File dest, boolean quietOn404) {
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream outStream = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "Zishu-Terminal/5.5 zgit");
            conn.setRequestProperty("Accept", "application/zip, application/octet-stream");

            int code = conn.getResponseCode();
            if (code == 404) {
                if (!quietOn404) {
                    writeMain("zgit: error: repository or branch not found (HTTP 404)");
                    writeMain("      check the URL and branch name");
                }
                return -1;
            }
            if (code < 200 || code >= 300) {
                writeMain("zgit: error: HTTP " + code + " " + conn.getResponseMessage());
                return -1;
            }

            long contentLength = conn.getContentLength();
            if (contentLength > MAX_DOWNLOAD_BYTES) {
                writeMain("zgit: error: download too large (" + formatSize(contentLength) + ")");
                return -1;
            }

            in = new BufferedInputStream(conn.getInputStream(), 16384);
            outStream = new BufferedOutputStream(new FileOutputStream(dest), 16384);

            byte[] buf = new byte[16384];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_DOWNLOAD_BYTES) {
                    writeMain("zgit: error: download exceeded " + formatSize(MAX_DOWNLOAD_BYTES));
                    return -1;
                }
                outStream.write(buf, 0, n);
            }
            outStream.flush();
            return total;

        } catch (IOException e) {
            writeMain("zgit: network error: " + e.getMessage());
            return -1;
        } finally {
            closeQuietly(in);
            closeQuietly(outStream);
            if (conn != null) conn.disconnect();
        }
    }

    // ------------------------------------------------------------------
    // ZIP extraction (safe)
    // ------------------------------------------------------------------

    /**
     * Extract ZIP into destDir. Rejects path traversal and oversized entries.
     * Returns number of files extracted.
     */
    private int extractZipSafe(File zipFile, File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("cannot create target dir: " + destDir.getAbsolutePath());
        }
        String destCanonical = destDir.getCanonicalPath();
        if (!destCanonical.endsWith(File.separator)) {
            destCanonical = destCanonical + File.separator;
        }

        ZipFile zf = null;
        int count = 0;
        long totalBytes = 0;
        try {
            zf = new ZipFile(zipFile);
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                // reject absolute and traversal
                if (name.startsWith("/") || name.startsWith("\\")) {
                    writeMain("zgit: warn: skipping absolute entry: " + name);
                    continue;
                }
                if (name.indexOf("..") >= 0) {
                    writeMain("zgit: warn: skipping traversal entry: " + name);
                    continue;
                }

                File target = new File(destDir, name);
                String targetCanonical = target.getCanonicalPath();
                if (!targetCanonical.startsWith(destCanonical)) {
                    writeMain("zgit: warn: skipping out-of-tree entry: " + name);
                    continue;
                }

                if (entry.isDirectory()) {
                    if (!target.exists() && !target.mkdirs()) {
                        throw new IOException("cannot create dir: " + target.getAbsolutePath());
                    }
                    continue;
                }

                // size check
                long declared = entry.getSize();
                if (declared > MAX_ENTRY_BYTES) {
                    writeMain("zgit: warn: skipping oversized entry: " + name);
                    continue;
                }

                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("cannot create dir: " + parent.getAbsolutePath());
                }

                InputStream is = null;
                OutputStream os = null;
                try {
                    is = new BufferedInputStream(zf.getInputStream(entry), 8192);
                    os = new BufferedOutputStream(new FileOutputStream(target), 8192);
                    byte[] buf = new byte[8192];
                    int n;
                    long entryTotal = 0;
                    while ((n = is.read(buf)) > 0) {
                        entryTotal += n;
                        totalBytes += n;
                        if (entryTotal > MAX_ENTRY_BYTES) {
                            throw new IOException("entry too large: " + name);
                        }
                        if (totalBytes > MAX_TOTAL_BYTES) {
                            throw new IOException("total extracted size exceeded");
                        }
                        os.write(buf, 0, n);
                    }
                    os.flush();
                    count++;
                } finally {
                    closeQuietly(is);
                    closeQuietly(os);
                }
            }
            return count;
        } finally {
            if (zf != null) {
                try { zf.close(); } catch (IOException ignored) { }
            }
        }
    }

    /**
     * GitHub ZIPs contain a single top-level dir like "repo-main/".
     * Move all its contents up into destDir, then remove the empty top dir.
     * This matches standard `git clone` behavior.
     *
     * Returns true if nothing needed to be done.
     */
    private boolean flattenTopDir(File destDir) throws IOException {
        File[] kids = destDir.listFiles();
        if (kids == null || kids.length != 1) return true;
        File only = kids[0];
        if (!only.isDirectory()) return true;

        // Move all contents of `only` up into destDir
        File[] contents = only.listFiles();
        if (contents == null || contents.length == 0) return true;

        for (int i = 0; i < contents.length; i++) {
            File src = contents[i];
            File dst = new File(destDir, src.getName());

            // Skip if a file/folder with the same name already exists at top level
            if (dst.exists()) {
                continue;
            }

            if (!src.renameTo(dst)) {
                // Fallback: copy + delete
                if (src.isDirectory()) {
                    copyDir(src, dst);
                    deleteRecursive(src);
                } else {
                    copyFile(src, dst);
                    src.delete();
                }
            }
        }

        // Remove the (now hopefully empty) top folder
        File[] remaining = only.listFiles();
        if (remaining == null || remaining.length == 0) {
            only.delete();
        }
        return false;
    }

    private void copyDir(File src, File dst) throws IOException {
        if (!dst.exists() && !dst.mkdirs()) {
            throw new IOException("cannot mkdir: " + dst.getAbsolutePath());
        }
        File[] kids = src.listFiles();
        if (kids == null) return;
        for (int i = 0; i < kids.length; i++) {
            File k = kids[i];
            File t = new File(dst, k.getName());
            if (k.isDirectory()) {
                copyDir(k, t);
            } else {
                copyFile(k, t);
            }
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new BufferedInputStream(new java.io.FileInputStream(src), 8192);
            os = new BufferedOutputStream(new FileOutputStream(dst), 8192);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
            os.flush();
        } finally {
            closeQuietly(is);
            closeQuietly(os);
        }
    }

    private void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (int i = 0; i < kids.length; i++) {
                    deleteRecursive(kids[i]);
                }
            }
        }
        f.delete();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void writeMain(String s) {
        out.writeLine(s);
    }

    private void closeQuietly(java.io.Closeable c) {
        if (c == null) return;
        try { c.close(); } catch (IOException ignored) { }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024L * 1024L) return (bytes / (1024L * 1024L)) + " MB";
        return (bytes / (1024L * 1024L * 1024L)) + " GB";
    }

    private String[] splitArgs(String line) {
        List<String> list = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == quoteChar) {
                    inQuote = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"' || c == '\'') {
                inQuote = true;
                quoteChar = c;
            } else if (c == ' ' || c == '\t') {
                if (cur.length() > 0) {
                    list.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) list.add(cur.toString());
        String[] arr = new String[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private void printHelp() {
        out.writeLine("zgit " + VERSION + " - GitHub repository cloner (Java-based)");
        out.writeLine("");
        out.writeLine("Usage:");
        out.writeLine("  zgit clone <url>              Clone to ./<repo>");
        out.writeLine("  zgit clone <url> <dir>        Clone to <dir>");
        out.writeLine("  zgit version");
        out.writeLine("  zgit help");
        out.writeLine("");
        out.writeLine("URL forms:");
        out.writeLine("  https://github.com/user/repo");
        out.writeLine("  https://github.com/user/repo.git");
        out.writeLine("  https://github.com/user/repo/tree/branch");
        out.writeLine("  git@github.com:user/repo.git");
        out.writeLine("");
        out.writeLine("Notes:");
        out.writeLine("  - Uses GitHub's official codeload ZIP endpoint");
        out.writeLine("  - Default branch: main (auto-falls back to master)");
        out.writeLine("  - Only public repositories");
        out.writeLine("  - Snapshot only: no git history, no pull/push");
    }

    // ------------------------------------------------------------------
    // Internal types
    // ------------------------------------------------------------------

    private static class RepoRef {
        String user;
        String repo;
        String branch;
    }
}