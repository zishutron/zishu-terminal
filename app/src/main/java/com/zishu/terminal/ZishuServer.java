package com.zishu.terminal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;

/**
 * Minimal HTTP server for serving a project directory over 127.0.0.1.
 * Binds to loopback only. No TLS, no directory traversal.
 */
public class ZishuServer {

    public interface Logger { void line(String s); }

    private final Logger logger;
    private ServerSocket socket;
    private Thread acceptThread;
    private volatile boolean running;

    public ZishuServer(Logger logger) { this.logger = logger; }

    public boolean isRunning() { return running; }

    public boolean start(final File root, final int port) {
        if (running) { log("Server already running."); return false; }
        if (root == null || !root.isDirectory()) { log("Invalid root."); return false; }
        try {
            socket = new ServerSocket(port, 16, java.net.InetAddress.getByName("127.0.0.1"));
        } catch (Throwable t) {
            log("Cannot bind port " + port + ": " + t.getMessage());
            return false;
        }
        running = true;
        acceptThread = new Thread(new Runnable() {
            @Override public void run() {
                while (running) {
                    try {
                        final Socket client = socket.accept();
                        new Thread(new Runnable() {
                            @Override public void run() { handle(client, root); }
                        }).start();
                    } catch (Throwable t) {
                        if (running) log("Accept error: " + t.getMessage());
                    }
                }
            }
        });
        acceptThread.setDaemon(true);
        acceptThread.setName("zishu-http");
        acceptThread.start();
        log("Server: http://127.0.0.1:" + port + "/");
        log("Root:   " + root.getAbsolutePath());
        return true;
    }

    public void stop() {
        running = false;
        try { if (socket != null) socket.close(); } catch (Throwable ignored) {}
        socket = null;
        log("Server stopped.");
    }

    private void handle(Socket client, File root) {
        OutputStream out = null;
        FileInputStream in = null;
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(client.getInputStream()));
            String first = r.readLine();
            if (first == null) { client.close(); return; }
            String[] parts = first.split(" ");
            String path = (parts.length > 1) ? parts[1] : "/";
            // Drain headers
            String line;
            while ((line = r.readLine()) != null && line.length() > 0) {}

            if (path.equals("/")) path = "/index.html";

            File target = new File(root, path).getCanonicalFile();
            if (!target.getPath().startsWith(root.getCanonicalPath())) {
                writeResponse(client, 403, "Forbidden", "text/plain", "Forbidden".getBytes("UTF-8"));
                return;
            }
            if (target.isDirectory()) {
                target = new File(target, "index.html");
            }
            if (!target.isFile()) {
                writeResponse(client, 404, "Not Found", "text/plain", "Not Found".getBytes("UTF-8"));
                return;
            }

            byte[] data = new byte[(int) Math.min(target.length(), 4 * 1024 * 1024)];
            in = new FileInputStream(target);
            int off = 0, n;
            while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
            byte[] body = new byte[off];
            System.arraycopy(data, 0, body, 0, off);

            writeResponse(client, 200, "OK", guessMime(target.getName()), body);
        } catch (Throwable t) {
            // silent close
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
            try { if (out != null) out.flush(); } catch (Throwable ignored) {}
            try { client.close(); } catch (Throwable ignored) {}
        }
    }

    private void writeResponse(Socket client, int code, String msg, String mime, byte[] body)
            throws IOException {
        OutputStream out = client.getOutputStream();
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 ").append(code).append(' ').append(msg).append("\r\n");
        h.append("Content-Type: ").append(mime).append("\r\n");
        h.append("Content-Length: ").append(body.length).append("\r\n");
        h.append("Connection: close\r\n\r\n");
        out.write(h.toString().getBytes("UTF-8"));
        out.write(body);
        out.flush();
    }

    private static String guessMime(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".txt") || n.endsWith(".md")) return "text/plain; charset=utf-8";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private void log(String s) { if (logger != null) logger.line(s); }
}