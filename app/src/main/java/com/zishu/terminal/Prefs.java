package com.zishu.terminal;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String FILE = "zishu_prefs";

    public static final String KEY_FONT_SIZE = "font_size";
    public static final String KEY_PADDING = "padding";
    public static final String KEY_THEME = "theme";
    public static final String KEY_CURSOR_STYLE = "cursor_style";
    public static final String KEY_CURSOR_BLINK = "cursor_blink";
    public static final String KEY_TIMESTAMPS = "timestamps";
    public static final String KEY_KEEP_AWAKE = "keep_awake";
    public static final String KEY_CONFIRM_CLEAR = "confirm_clear";
    public static final String KEY_HISTORY_SIZE = "history_size";

    public static final int THEME_DARK = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_GREEN = 2;

    public static final int DEFAULT_FONT_SIZE = 14;
    public static final int DEFAULT_PADDING = 8;
    public static final int DEFAULT_THEME = THEME_DARK;
    public static final int DEFAULT_HISTORY_SIZE = 500;

    private Prefs() {}

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int getInt(Context c, String key, int def) {
        return get(c).getInt(key, def);
    }

    public static void putInt(Context c, String key, int v) {
        get(c).edit().putInt(key, v).apply();
    }

    public static boolean getBool(Context c, String key, boolean def) {
        return get(c).getBoolean(key, def);
    }

    public static void putBool(Context c, String key, boolean v) {
        get(c).edit().putBoolean(key, v).apply();
    }

    public static String getString(Context c, String key, String def) {
        return get(c).getString(key, def);
    }

    public static void putString(Context c, String key, String v) {
        get(c).edit().putString(key, v).apply();
    }

    public static void reset(Context c) {
        get(c).edit().clear().apply();
    }
}
