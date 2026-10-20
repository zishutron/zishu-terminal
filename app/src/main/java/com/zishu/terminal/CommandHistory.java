package com.zishu.terminal;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class CommandHistory {
    private static final String KEY = "cmd_history_v1";

    private final Context ctx;
    private final ArrayList<String> history = new ArrayList<String>();
    private int max;

    public CommandHistory(Context c) {
        this.ctx = c.getApplicationContext();
        max = Prefs.getInt(ctx, Prefs.KEY_HISTORY_SIZE, Prefs.DEFAULT_HISTORY_SIZE);
        load();
    }

    public void setMax(int m) {
        if (m < 10) m = 10;
        if (m > 5000) m = 5000;
        max = m;
        trim();
        save();
    }

    public int getMax() { return max; }

    public void add(String cmd) {
        if (cmd == null) return;
        cmd = cmd.trim();
        if (cmd.length() == 0) return;
        if (history.size() > 0 && history.get(history.size() - 1).equals(cmd)) return;
        history.add(cmd);
        trim();
        save();
    }

    public String get(int indexFromEnd) {
        int idx = history.size() - 1 - indexFromEnd;
        if (idx < 0 || idx >= history.size()) return null;
        return history.get(idx);
    }

    public int size() { return history.size(); }

    public List<String> all() { return new ArrayList<String>(history); }

    public void clear() {
        history.clear();
        save();
    }

    private void trim() {
        while (history.size() > max) history.remove(0);
    }

    private void load() {
        SharedPreferences sp = Prefs.get(ctx);
        String blob = sp.getString(KEY, "");
        if (blob == null || blob.length() == 0) return;
        String[] parts = blob.split("\n");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].length() > 0) history.add(parts[i]);
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            sb.append(history.get(i));
            if (i < history.size() - 1) sb.append('\n');
        }
        Prefs.get(ctx).edit().putString(KEY, sb.toString()).apply();
    }
}