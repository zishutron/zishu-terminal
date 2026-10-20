package com.zishu.terminal;

import android.content.Context;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Renders the startup banner for Zishu Terminal.
 *
 * Supports 5 built-in styles + custom banner + none.
 * Uses ANSI escape sequences for coloring (rendered by AnsiParser).
 */
public final class BannerRenderer {

    public static final String VERSION = "v5.6.5";

    private BannerRenderer() {}

    /**
     * Build the full startup banner. Returns a single multi-line string.
     * Caller can split and append to terminal.
     */
    public static String render(Context ctx) {
        if (!StartupPrefs.isBannerEnabled(ctx)) {
            return null;
        }
        int style = StartupPrefs.getBannerStyle(ctx);
        if (style == StartupPrefs.BANNER_NONE) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        String color = StartupPrefs.ansiColorFor(StartupPrefs.getBannerColor(ctx));
        String bold  = StartupPrefs.ANSI_BOLD;
        String reset = StartupPrefs.ANSI_RESET;

        // ---- Banner art ----
        String art;
        switch (style) {
            case StartupPrefs.BANNER_ASCII:   art = artAscii();      break;
            case StartupPrefs.BANNER_BOX:     art = artBox();        break;
            case StartupPrefs.BANNER_MINIMAL: art = artMinimal();    break;
            case StartupPrefs.BANNER_HACKER:  art = artHacker();     break;
            case StartupPrefs.BANNER_CUSTOM:  art = customArt(ctx);  break;
            case StartupPrefs.BANNER_BLOCK:
            default:                          art = artBlock();      break;
        }

        sb.append(color).append(bold).append(art).append(reset);

        // ---- Extras ----
        StringBuilder extras = new StringBuilder();

        if (StartupPrefs.showVersion(ctx)) {
            extras.append("  Zishu Terminal ").append(VERSION).append("\n");
        }
        if (StartupPrefs.showDevice(ctx)) {
            extras.append("  Device: ")
                  .append(safe(Build.MANUFACTURER)).append(" ")
                  .append(safe(Build.MODEL))
                  .append(" (Android ").append(safe(Build.VERSION.RELEASE))
                  .append(")\n");
        }
        if (StartupPrefs.showDate(ctx)) {
            String date = new SimpleDateFormat("EEE, dd MMM yyyy  HH:mm",
                    Locale.US).format(new Date());
            extras.append("  ").append(date).append("\n");
        }

        String welcome = StartupPrefs.getWelcomeText(ctx);
        if (welcome != null && welcome.length() > 0) {
            extras.append("\n  ").append(welcome).append("\n");
        }

        extras.append("  Type 'zhelp' for available commands.\n");

        if (extras.length() > 0) {
            sb.append("\n").append(extras);
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Built-in art
    // ---------------------------------------------------------------

    /**
     * Unicode block letters. Very clear, renders cleanly in terminals
     * that support box-drawing and block characters (all modern Android
     * terminals do). This is the default.
     */
    private static String artBlock() {
        return
              "  ███████╗██╗███████╗██╗  ██╗██╗   ██╗\n"
            + "  ╚══███╔╝██║██╔════╝██║  ██║██║   ██║\n"
            + "    ███╔╝ ██║███████╗███████║██║   ██║\n"
            + "   ███╔╝  ██║╚════██║██╔══██║██║   ██║\n"
            + "  ███████╗██║███████║██║  ██║╚██████╔╝\n"
            + "  ╚══════╝╚═╝╚══════╝╚═╝  ╚═╝ ╚═════╝ \n";
    }

    /**
     * Classic ASCII art. Clearer than "slant" but not as clear as block.
     */
    private static String artAscii() {
        return
              "  _____ _____  _____ _    _ _   _\n"
            + " |__  /_   _|/ ____| |  | | | | |\n"
            + "   / /  | | | (___ | |__| | | | |\n"
            + "  / /   | |  \\___ \\|  __  | | | |\n"
            + " / /__ _| |_ ____) | |  | | |_| |\n"
            + "/_____|_____|_____/|_|  |_|\\___/ \n";
    }

    private static String artBox() {
        return
              "  ╔══════════════════════════════════╗\n"
            + "  ║                                  ║\n"
            + "  ║          ZISHU TERMINAL          ║\n"
            + "  ║            " + VERSION + "             ║\n"
            + "  ║                                  ║\n"
            + "  ╚══════════════════════════════════╝\n";
    }

    private static String artMinimal() {
        return
              "  ZISHU TERMINAL " + VERSION + "\n"
            + "  ─────────────────────────────\n";
    }

    private static String artHacker() {
        return
              "  ┌──────────────────────────────┐\n"
            + "  │  [ Z I S H U   T E R M I N A L ]\n"
            + "  └──────────────────────────────┘\n";
    }

    private static String customArt(Context ctx) {
        String custom = StartupPrefs.getCustomBanner(ctx);
        if (custom == null || custom.trim().length() == 0) {
            return artBlock();  // fallback
        }
        return custom;
    }

    private static String safe(String s) {
        return s == null ? "unknown" : s;
    }
}