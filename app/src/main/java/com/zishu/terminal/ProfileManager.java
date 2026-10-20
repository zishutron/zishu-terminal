package com.zishu.terminal;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProfileManager {
    private static final String KEY_PREFIX = "profile.";
    private final Context ctx;

    public ProfileManager(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    public List<TerminalProfile> all() {
        SharedPreferences sp = Prefs.get(ctx);
        Map<String, ?> all = sp.getAll();
        List<String> names = new ArrayList<String>();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            String k = e.getKey();
            if (k.startsWith(KEY_PREFIX) && k.endsWith(".fontSize")) {
                names.add(k.substring(KEY_PREFIX.length(), k.length() - ".fontSize".length()));
            }
        }
        if (names.isEmpty()) names.add("Default");
        List<TerminalProfile> out = new ArrayList<TerminalProfile>();
        for (int i = 0; i < names.size(); i++) out.add(load(names.get(i)));
        return out;
    }

    public TerminalProfile load(String name) {
        TerminalProfile p = new TerminalProfile(name);
        SharedPreferences sp = Prefs.get(ctx);
        String pfx = KEY_PREFIX + name + ".";
        p.fontSize = sp.getInt(pfx + "fontSize", 14);
        p.theme = sp.getInt(pfx + "theme", Prefs.THEME_DARK);
        p.prompt = sp.getString(pfx + "prompt", "zishu@android");
        p.cursorStyle = sp.getInt(pfx + "cursorStyle", 0);
        p.cursorBlink = sp.getBoolean(pfx + "cursorBlink", true);
        p.keyboardBar = sp.getBoolean(pfx + "keyboardBar", true);
        p.extraPath = sp.getString(pfx + "extraPath", "");
        p.startDir = sp.getString(pfx + "startDir", "");
        return p;
    }

    public void save(TerminalProfile p) {
        SharedPreferences.Editor e = Prefs.get(ctx).edit();
        String pfx = KEY_PREFIX + p.name + ".";
        e.putInt(pfx + "fontSize", p.fontSize);
        e.putInt(pfx + "theme", p.theme);
        e.putString(pfx + "prompt", p.prompt);
        e.putInt(pfx + "cursorStyle", p.cursorStyle);
        e.putBoolean(pfx + "cursorBlink", p.cursorBlink);
        e.putBoolean(pfx + "keyboardBar", p.keyboardBar);
        e.putString(pfx + "extraPath", p.extraPath);
        e.putString(pfx + "startDir", p.startDir);
        e.apply();
    }

    public void delete(String name) {
        SharedPreferences.Editor e = Prefs.get(ctx).edit();
        String pfx = KEY_PREFIX + name + ".";
        e.remove(pfx + "fontSize");
        e.remove(pfx + "theme");
        e.remove(pfx + "prompt");
        e.remove(pfx + "cursorStyle");
        e.remove(pfx + "cursorBlink");
        e.remove(pfx + "keyboardBar");
        e.remove(pfx + "extraPath");
        e.remove(pfx + "startDir");
        e.apply();
    }
}