package com.zishu.terminal;

import android.content.Context;

/**
 * Startup & Home customization settings for Zishu Terminal.
 */
public final class StartupPrefs {

    // ---- Keys ----
    public static final String KEY_BANNER_ENABLED  = "startup_banner_enabled";
    public static final String KEY_BANNER_STYLE    = "startup_banner_style";
    public static final String KEY_BANNER_CUSTOM   = "startup_banner_custom";
    public static final String KEY_SHOW_VERSION    = "startup_show_version";
    public static final String KEY_SHOW_DATE       = "startup_show_date";
    public static final String KEY_SHOW_DEVICE     = "startup_show_device";
    public static final String KEY_WELCOME_TEXT    = "startup_welcome_text";
    public static final String KEY_BANNER_COLOR    = "startup_banner_color";

    // ---- Banner styles ----
    public static final int BANNER_BLOCK    = 0;   // Unicode block (default)
    public static final int BANNER_ASCII    = 1;   // Classic ASCII art
    public static final int BANNER_BOX      = 2;
    public static final int BANNER_MINIMAL  = 3;
    public static final int BANNER_HACKER   = 4;
    public static final int BANNER_CUSTOM   = 5;
    public static final int BANNER_NONE     = 6;

    // ---- Banner colors ----
    public static final int COLOR_ACCENT = 0;
    public static final int COLOR_WHITE  = 1;
    public static final int COLOR_GREEN  = 2;
    public static final int COLOR_CYAN   = 3;
    public static final int COLOR_YELLOW = 4;
    public static final int COLOR_RED    = 5;

    // ---- Defaults ----
    public static final boolean DEFAULT_BANNER_ENABLED = true;
    public static final int     DEFAULT_BANNER_STYLE   = BANNER_ASCII;
    public static final boolean DEFAULT_SHOW_VERSION   = true;
    public static final boolean DEFAULT_SHOW_DATE      = false;
    public static final boolean DEFAULT_SHOW_DEVICE    = false;
    public static final int     DEFAULT_BANNER_COLOR   = COLOR_ACCENT;
    public static final String  DEFAULT_WELCOME_TEXT   = "Welcome back, zishu!";

    private StartupPrefs() {}

    public static boolean isBannerEnabled(Context c) {
        return Prefs.getBool(c, KEY_BANNER_ENABLED, DEFAULT_BANNER_ENABLED);
    }

    public static void setBannerEnabled(Context c, boolean on) {
        Prefs.putBool(c, KEY_BANNER_ENABLED, on);
    }

    public static int getBannerStyle(Context c) {
        return Prefs.getInt(c, KEY_BANNER_STYLE, DEFAULT_BANNER_STYLE);
    }

    public static void setBannerStyle(Context c, int style) {
        Prefs.putInt(c, KEY_BANNER_STYLE, style);
    }

    public static String getCustomBanner(Context c) {
        return Prefs.get(c).getString(KEY_BANNER_CUSTOM, "");
    }

    public static void setCustomBanner(Context c, String text) {
        Prefs.get(c).edit().putString(KEY_BANNER_CUSTOM, text == null ? "" : text).apply();
    }

    public static boolean showVersion(Context c) {
        return Prefs.getBool(c, KEY_SHOW_VERSION, DEFAULT_SHOW_VERSION);
    }

    public static void setShowVersion(Context c, boolean on) {
        Prefs.putBool(c, KEY_SHOW_VERSION, on);
    }

    public static boolean showDate(Context c) {
        return Prefs.getBool(c, KEY_SHOW_DATE, DEFAULT_SHOW_DATE);
    }

    public static void setShowDate(Context c, boolean on) {
        Prefs.putBool(c, KEY_SHOW_DATE, on);
    }

    public static boolean showDevice(Context c) {
        return Prefs.getBool(c, KEY_SHOW_DEVICE, DEFAULT_SHOW_DEVICE);
    }

    public static void setShowDevice(Context c, boolean on) {
        Prefs.putBool(c, KEY_SHOW_DEVICE, on);
    }

    public static String getWelcomeText(Context c) {
        return Prefs.get(c).getString(KEY_WELCOME_TEXT, DEFAULT_WELCOME_TEXT);
    }

    public static void setWelcomeText(Context c, String text) {
        Prefs.get(c).edit().putString(KEY_WELCOME_TEXT,
                text == null ? DEFAULT_WELCOME_TEXT : text).apply();
    }

    public static int getBannerColor(Context c) {
        return Prefs.getInt(c, KEY_BANNER_COLOR, DEFAULT_BANNER_COLOR);
    }

    public static void setBannerColor(Context c, int color) {
        Prefs.putInt(c, KEY_BANNER_COLOR, color);
    }

    public static String bannerStyleName(int style) {
        switch (style) {
            case BANNER_BLOCK:   return "Block";
            case BANNER_ASCII:   return "ASCII Art";
            case BANNER_BOX:     return "Box";
            case BANNER_MINIMAL: return "Minimal";
            case BANNER_HACKER:  return "Hacker";
            case BANNER_CUSTOM:  return "Custom";
            case BANNER_NONE:    return "None";
            default:             return "Unknown";
        }
    }

    public static String bannerColorName(int color) {
        switch (color) {
            case COLOR_ACCENT: return "Accent";
            case COLOR_WHITE:  return "White";
            case COLOR_GREEN:  return "Green";
            case COLOR_CYAN:   return "Cyan";
            case COLOR_YELLOW: return "Yellow";
            case COLOR_RED:    return "Red";
            default:           return "Unknown";
        }
    }

    public static String ansiColorFor(int color) {
        switch (color) {
            case COLOR_WHITE:  return "\u001b[97m";
            case COLOR_GREEN:  return "\u001b[92m";
            case COLOR_CYAN:   return "\u001b[96m";
            case COLOR_YELLOW: return "\u001b[93m";
            case COLOR_RED:    return "\u001b[91m";
            case COLOR_ACCENT:
            default:           return "\u001b[96m";
        }
    }

    public static final String ANSI_RESET = "\u001b[0m";
    public static final String ANSI_BOLD  = "\u001b[1m";
    public static final String ANSI_DIM   = "\u001b[2m";
}
