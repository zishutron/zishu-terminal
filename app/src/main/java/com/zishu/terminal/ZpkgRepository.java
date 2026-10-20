package com.zishu.terminal;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Repository configuration for zpkg. A repository is (name, url, enabled).
 * Persisted under SharedPreferences keys repo.<name>.*
 */
public class ZpkgRepository {

    public static class Repo {
        public String name;
        public String url;
        public boolean enabled = true;
        public Repo(String name, String url) { this.name = name; this.url = url; }
    }

    private static final String PREFIX = "repo.";
    private final Context ctx;

    public ZpkgRepository(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    public List<Repo> all() {
        SharedPreferences sp = Prefs.get(ctx);
        List<String> names = new ArrayList<String>();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            String k = e.getKey();
            if (k.startsWith(PREFIX) && k.endsWith(".url")) {
                names.add(k.substring(PREFIX.length(), k.length() - ".url".length()));
            }
        }
        List<Repo> out = new ArrayList<Repo>();
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            Repo r = new Repo(n, sp.getString(PREFIX + n + ".url", ""));
            r.enabled = sp.getBoolean(PREFIX + n + ".enabled", true);
            out.add(r);
        }
        return out;
    }

    public void add(Repo r) {
        SharedPreferences.Editor e = Prefs.get(ctx).edit();
        e.putString(PREFIX + r.name + ".url", r.url);
        e.putBoolean(PREFIX + r.name + ".enabled", r.enabled);
        e.apply();
    }

    public void remove(String name) {
        SharedPreferences.Editor e = Prefs.get(ctx).edit();
        e.remove(PREFIX + name + ".url");
        e.remove(PREFIX + name + ".enabled");
        e.apply();
    }

    public void enable(String name, boolean on) {
        Prefs.get(ctx).edit()
                .putBoolean(PREFIX + name + ".enabled", on)
                .apply();
    }
}