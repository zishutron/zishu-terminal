package com.zishu.terminal;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

/**
 * Real ANSI escape-sequence parser. Handles SGR (color/bold/underline)
 * and produces a styled Spannable. Unsupported sequences are silently
 * skipped so output never contains visible garbage.
 */
public class AnsiParser {

    private static final int[] ANSI_COLORS = new int[]{
            0xFF000000, // 0 black
            0xFFAA0000, // 1 red
            0xFF00AA00, // 2 green
            0xFFAA5500, // 3 yellow/brown
            0xFF0000AA, // 4 blue
            0xFFAA00AA, // 5 magenta
            0xFF00AAAA, // 6 cyan
            0xFFAAAAAA  // 7 white
    };

    private static final int[] ANSI_BRIGHT = new int[]{
            0xFF555555, 0xFFFF5555, 0xFF55FF55, 0xFFFFFF55,
            0xFF5555FF, 0xFFFF55FF, 0xFF55FFFF, 0xFFFFFFFF
    };

    private int fgColor = -1;
    private int bgColor = -1;
    private boolean bold = false;
    private boolean underline = false;
    private boolean reverse = false;

    /** Parse a raw line into a Spannable; strips ANSI codes. */
    public CharSequence parse(String raw, int defaultFg) {
        if (raw == null) return "";
        SpannableStringBuilder out = new SpannableStringBuilder();
        StringBuilder plain = new StringBuilder();
        int i = 0;
        int n = raw.length();
        while (i < n) {
            char c = raw.charAt(i);
            if (c == 0x1B && i + 1 < n && raw.charAt(i + 1) == '[') {
                // CSI sequence
                int j = i + 2;
                while (j < n) {
                    char cc = raw.charAt(j);
                    if (cc >= '@' && cc <= '~') break;
                    j++;
                }
                if (j < n) {
                    char cmd = raw.charAt(j);
                    String args = raw.substring(i + 2, j);
                    if (cmd == 'm') {
                        applySgr(args);
                    }
                    // Other commands (H, J, K, etc.) — not rendered in TextView.
                    // We just strip them to avoid visible garbage.
                }
                i = j + 1;
            } else if (c == '\r') {
                // Carriage return: for our line-based model, ignore
                i++;
            } else if (c == '\b') {
                // Backspace: remove last char
                if (plain.length() > 0) {
                    plain.deleteCharAt(plain.length() - 1);
                }
                i++;
            } else {
                plain.append(c);
                i++;
            }
        }

        int start = out.length();
        out.append(plain.toString());
        int end = out.length();

        int color = fgColor >= 0 ? fgColor : defaultFg;
        if (bold && color == defaultFg) {
            // approximate bold for text
        }
        if (color >= 0) out.setSpan(new ForegroundColorSpan(color),
                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) out.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (underline) out.setSpan(new UnderlineSpan(),
                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        // reset state per line (common practice for line-based terminal)
        fgColor = -1;
        bgColor = -1;
        bold = false;
        underline = false;
        reverse = false;
        return out;
    }

    private void applySgr(String args) {
        if (args == null || args.length() == 0) {
            resetAttrs();
            return;
        }
        String[] parts = args.split(";");
        for (int i = 0; i < parts.length; i++) {
            int v;
            try { v = Integer.parseInt(parts[i].trim()); }
            catch (Throwable t) { continue; }
            if (v == 0) resetAttrs();
            else if (v == 1) bold = true;
            else if (v == 4) underline = true;
            else if (v == 7) reverse = true;
            else if (v == 22) bold = false;
            else if (v == 24) underline = false;
            else if (v == 27) reverse = false;
            else if (v >= 30 && v <= 37) fgColor = ANSI_COLORS[v - 30];
            else if (v >= 90 && v <= 97) fgColor = ANSI_BRIGHT[v - 90];
            else if (v == 39) fgColor = -1;
            else if (v >= 40 && v <= 47) bgColor = ANSI_COLORS[v - 40];
            else if (v >= 100 && v <= 107) bgColor = ANSI_BRIGHT[v - 100];
            else if (v == 49) bgColor = -1;
            else if (v == 38 || v == 48) {
                // Extended color: 38;5;N or 38;2;R;G;B
                if (i + 1 < parts.length) {
                    int mode;
                    try { mode = Integer.parseInt(parts[i + 1].trim()); }
                    catch (Throwable t) { continue; }
                    if (mode == 5 && i + 2 < parts.length) {
                        int idx;
                        try { idx = Integer.parseInt(parts[i + 2].trim()); }
                        catch (Throwable t) { continue; }
                        int col = color256(idx);
                        if (v == 38) fgColor = col; else bgColor = col;
                        i += 2;
                    } else if (mode == 2 && i + 4 < parts.length) {
                        try {
                            int r = Integer.parseInt(parts[i + 2].trim());
                            int g = Integer.parseInt(parts[i + 3].trim());
                            int b = Integer.parseInt(parts[i + 4].trim());
                            int col = 0xFF000000 | (r << 16) | (g << 8) | b;
                            if (v == 38) fgColor = col; else bgColor = col;
                            i += 4;
                        } catch (Throwable t) { /* skip */ }
                    }
                }
            }
        }
    }

    private void resetAttrs() {
        fgColor = -1; bgColor = -1;
        bold = false; underline = false; reverse = false;
    }

    private static int color256(int idx) {
        if (idx < 16) return idx < 8 ? ANSI_COLORS[idx] : ANSI_BRIGHT[idx - 8];
        if (idx < 232) {
            int c = idx - 16;
            int r = c / 36;
            int g = (c % 36) / 6;
            int b = c % 6;
            int[] steps = {0, 95, 135, 175, 215, 255};
            return 0xFF000000 | (steps[r] << 16) | (steps[g] << 8) | steps[b];
        }
        int gray = 8 + (idx - 232) * 10;
        return 0xFF000000 | (gray << 16) | (gray << 8) | gray;
    }
}