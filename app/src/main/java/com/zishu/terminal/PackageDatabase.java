package com.zishu.terminal;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple line-based package database. Format (one package per block):
 *
 *   name=<name>
 *   version=<version>
 *   description=<desc>
 *   architecture=<abi>
 *   source=<source>
 *   integrity=<hash>
 *   installedAt=<ms>
 *   files=<path1>|<path2>|...
 *   deps=<name1>|<name2>|...
 *   ---
 *
 * The format is trivial and dependency-free so Code Studio can read/write
 * it without any external parser. Corruption is handled by skipping bad
 * records, never crashing.
 */
public class PackageDatabase {

    private final File dbFile;
    private final List<PackageInfo> packages = new ArrayList<PackageInfo>();

    public PackageDatabase(Context ctx) {
        dbFile = new ZishuEnv(ctx).getPkgDbFile();
        load();
    }

    public List<PackageInfo> all() { return new ArrayList<PackageInfo>(packages); }

    public PackageInfo byName(String name) {
        for (int i = 0; i < packages.size(); i++) {
            if (packages.get(i).name.equals(name)) return packages.get(i);
        }
        return null;
    }

    public boolean add(PackageInfo p) {
        if (p == null || p.name == null || p.name.length() == 0) return false;
        if (byName(p.name) != null) return false;
        packages.add(p);
        return save();
    }

    public boolean remove(String name) {
        PackageInfo p = byName(name);
        if (p == null) return false;
        packages.remove(p);
        return save();
    }

    public boolean save() {
        FileWriter w = null;
        try {
            File parent = dbFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            w = new FileWriter(dbFile, false);
            for (int i = 0; i < packages.size(); i++) {
                PackageInfo p = packages.get(i);
                w.write("name=" + safe(p.name) + "\n");
                w.write("version=" + safe(p.version) + "\n");
                w.write("description=" + safe(p.description) + "\n");
                w.write("architecture=" + safe(p.architecture) + "\n");
                w.write("source=" + safe(p.source) + "\n");
                w.write("integrity=" + safe(p.integrity) + "\n");
                w.write("installedAt=" + p.installedAt + "\n");
                w.write("files=" + join(p.files) + "\n");
                w.write("deps=" + join(p.dependencies) + "\n");
                w.write("---\n");
            }
            w.flush();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (w != null) try { w.close(); } catch (Throwable ignored) {}
        }
    }

    private void load() {
        packages.clear();
        if (dbFile == null || !dbFile.exists()) return;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(dbFile));
            String line;
            PackageInfo cur = new PackageInfo();
            while ((line = r.readLine()) != null) {
                if (line.equals("---")) {
                    if (cur.name != null && cur.name.length() > 0) packages.add(cur);
                    cur = new PackageInfo();
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq);
                String v = line.substring(eq + 1);
                if (k.equals("name")) cur.name = v;
                else if (k.equals("version")) cur.version = v;
                else if (k.equals("description")) cur.description = v;
                else if (k.equals("architecture")) cur.architecture = v;
                else if (k.equals("source")) cur.source = v;
                else if (k.equals("integrity")) cur.integrity = v;
                else if (k.equals("installedAt")) {
                    try { cur.installedAt = Long.parseLong(v); } catch (Throwable ignored) {}
                } else if (k.equals("files")) cur.files = split(v);
                else if (k.equals("deps")) cur.dependencies = split(v);
            }
            if (cur.name != null && cur.name.length() > 0) packages.add(cur);
        } catch (Throwable t) {
            // Corrupt file — start with empty list, do not crash.
            packages.clear();
        } finally {
            if (r != null) try { r.close(); } catch (Throwable ignored) {}
        }
    }

    private static String safe(String s) { return s == null ? "" : s.replace("\n", " "); }

    private static String join(String[] arr) {
        if (arr == null || arr.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static String[] split(String s) {
        if (s == null || s.length() == 0) return new String[0];
        return s.split("\\|");
    }
}