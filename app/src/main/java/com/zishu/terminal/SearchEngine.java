package com.zishu.terminal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Real local file/content search. Runs on caller thread — the caller
 * is responsible for putting it on a background thread.
 */
public class SearchEngine {

    public interface Cancel { boolean cancelled(); }

    public static class Hit {
        public File file;
        public int line;
        public String text;
    }

    public static List<Hit> search(File root, String term, boolean inContent,
                                   int maxResults, Cancel cancel) {
        List<Hit> out = new ArrayList<Hit>();
        if (root == null || term == null || term.length() == 0) return out;
        walk(root, term, inContent, out, maxResults, cancel);
        return out;
    }

    private static void walk(File dir, String term, boolean inContent,
                             List<Hit> out, int max, Cancel cancel) {
        if (out.size() >= max) return;
        if (cancel != null && cancel.cancelled()) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (int i = 0; i < kids.length; i++) {
            if (out.size() >= max) return;
            if (cancel != null && cancel.cancelled()) return;
            File f = kids[i];
            String name = f.getName();
            if (name.startsWith(".")) continue;  // skip hidden
            if (f.isDirectory()) {
                walk(f, term, inContent, out, max, cancel);
            } else {
                if (f.length() > 2 * 1024 * 1024) continue; // skip huge
                if (inContent) {
                    grepFile(f, term, out, max);
                } else {
                    if (f.getName().toLowerCase().contains(term.toLowerCase())) {
                        Hit h = new Hit();
                        h.file = f; h.line = 0; h.text = f.getName();
                        out.add(h);
                    }
                }
            }
        }
    }

    private static void grepFile(File f, String term, List<Hit> out, int max) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(f));
            String line;
            int ln = 0;
            while ((line = r.readLine()) != null) {
                ln++;
                if (line.contains(term)) {
                    Hit h = new Hit();
                    h.file = f; h.line = ln; h.text = line;
                    out.add(h);
                    if (out.size() >= max) return;
                }
            }
        } catch (Throwable ignored) {}
        finally { if (r != null) try { r.close(); } catch (Throwable ignored) {} }
    }
}