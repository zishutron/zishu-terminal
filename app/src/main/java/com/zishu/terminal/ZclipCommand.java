package com.zishu.terminal;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * zclip - Clipboard manager for Zishu Terminal.
 *
 * Commands:
 *   zclip                       Help
 *   zclip copy                  Copy full output
 *   zclip copy <N>              Copy last N lines
 *   zclip copy line <N>         Copy line N
 *   zclip copy <start> <end>    Copy lines start..end
 *   zclip paste                 Print clipboard
 *   zclip paste input           Insert clipboard into command input
 *   zclip show                  Print clipboard (same as paste)
 *   zclip len                   Show clipboard length
 *   zclip clear                 Clear clipboard
 *   zclip save <file>           Save clipboard to file
 *   zclip load <file>           Load file into clipboard
 *   zclip search <term>         Search clipboard
 *   zclip replace <old> <new>   Replace in clipboard
 *   zclip trim                  Trim whitespace
 *   zclip reverse               Reverse lines
 *   zclip sort                  Sort lines
 *   zclip uniq                  Remove duplicate lines
 *   zclip history               Show last 10 clipboard items
 *   zclip undo                  Restore last clipboard
 */
public class ZclipCommand {

    public interface Output {
        void print(String s);
    }

    public interface InputHandler {
        void setInput(String text);
    }

    private static final String HISTORY_KEY = "zclip_history";
    private static final int HISTORY_LIMIT = 10;

    private final Context ctx;
    private final Output out;
    private final InputHandler inputHandler;
    private final SessionManager mgr;

    public ZclipCommand(Context ctx, Output out, InputHandler inputHandler) {
        this.ctx = ctx.getApplicationContext();
        this.out = out;
        this.inputHandler = inputHandler;
        this.mgr = SessionManager.get();
    }

    /**
     * Entry point. Returns true if handled.
     */
    public boolean handle(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        if (!trimmed.equals("zclip") && !trimmed.startsWith("zclip ")) {
            return false;
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2) {
            printHelp();
            return true;
        }

        String sub = parts[1].toLowerCase(Locale.US);

        if (sub.equals("help") || sub.equals("--help") || sub.equals("-h")) {
            printHelp();
            return true;
        }

        if (sub.equals("copy")) {
            handleCopy(parts);
            return true;
        }
        if (sub.equals("paste")) {
            handlePaste(parts);
            return true;
        }
        if (sub.equals("show")) {
            handleShow();
            return true;
        }
        if (sub.equals("len")) {
            handleLen();
            return true;
        }
        if (sub.equals("clear")) {
            handleClear();
            return true;
        }
        if (sub.equals("save")) {
            handleSave(parts);
            return true;
        }
        if (sub.equals("load")) {
            handleLoad(parts);
            return true;
        }
        if (sub.equals("search")) {
            handleSearch(parts);
            return true;
        }
        if (sub.equals("replace")) {
            handleReplace(parts);
            return true;
        }
        if (sub.equals("trim")) {
            handleTransform("trim");
            return true;
        }
        if (sub.equals("reverse")) {
            handleTransform("reverse");
            return true;
        }
        if (sub.equals("sort")) {
            handleTransform("sort");
            return true;
        }
        if (sub.equals("uniq")) {
            handleTransform("uniq");
            return true;
        }
        if (sub.equals("history")) {
            handleHistory();
            return true;
        }
        if (sub.equals("undo")) {
            handleUndo();
            return true;
        }

        out.print("Unknown zclip subcommand: " + sub);
        out.print("Run 'zclip help' for usage.");
        return true;
    }

    // ================================================================
    // COPY
    // ================================================================

    private void handleCopy(String[] parts) {
        String text = null;

        // zclip copy
        if (parts.length == 2) {
            text = getFullOutput();
            if (text == null || text.length() == 0) {
                out.print("zclip: terminal is empty.");
                return;
            }
        }
        // zclip copy line <N>
        else if (parts.length == 4 && parts[2].equalsIgnoreCase("line")) {
            int n = parseInt(parts[3], -1);
            if (n < 1) {
                out.print("zclip: invalid line number: " + parts[3]);
                return;
            }
            text = getLine(n);
            if (text == null) {
                out.print("zclip: line " + n + " not found.");
                return;
            }
        }
        // zclip copy <N>
        else if (parts.length == 3) {
            int n = parseInt(parts[2], -1);
            if (n < 1) {
                out.print("zclip: invalid line count: " + parts[2]);
                return;
            }
            text = getLastLines(n);
            if (text == null || text.length() == 0) {
                out.print("zclip: terminal is empty.");
                return;
            }
        }
        // zclip copy <start> <end>
        else if (parts.length == 4) {
            int start = parseInt(parts[2], -1);
            int end = parseInt(parts[3], -1);
            if (start < 1 || end < 1 || start > end) {
                out.print("zclip: invalid range: " + parts[2] + ".." + parts[3]);
                return;
            }
            text = getRange(start, end);
            if (text == null || text.length() == 0) {
                out.print("zclip: no lines in range.");
                return;
            }
        }
        else {
            out.print("zclip: invalid arguments.");
            out.print("Usage: zclip copy | zclip copy <N> | zclip copy line <N> | zclip copy <start> <end>");
            return;
        }

        // Push history
        pushHistory(getClipboard());

        // Set clipboard
        setClipboard(text);

        int lines = text.split("\n", -1).length;
        out.print("zclip: copied " + text.length() + " chars, " + lines + " lines.");
    }

    // ================================================================
    // PASTE
    // ================================================================

    private void handlePaste(String[] parts) {
        String clip = getClipboard();
        if (clip == null || clip.length() == 0) {
            out.print("zclip: clipboard is empty.");
            return;
        }

        // zclip paste input
        if (parts.length >= 3 && parts[2].equalsIgnoreCase("input")) {
            if (inputHandler != null) {
                inputHandler.setInput(clip);
                out.print("zclip: inserted " + clip.length() + " chars into command input.");
                out.print("       Press Enter to run (or edit first).");
            } else {
                out.print("zclip: input handler unavailable.");
            }
            return;
        }

        // zclip paste
        out.print("─── Clipboard ───");
        String[] lines = clip.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            out.print(lines[i]);
        }
        out.print("─── End (" + clip.length() + " chars) ───");
    }

    // ================================================================
    // SHOW
    // ================================================================

    private void handleShow() {
        String clip = getClipboard();
        if (clip == null || clip.length() == 0) {
            out.print("zclip: clipboard is empty.");
            return;
        }
        String[] lines = clip.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            out.print(lines[i]);
        }
    }

    // ================================================================
    // LEN
    // ================================================================

    private void handleLen() {
        String clip = getClipboard();
        if (clip == null) clip = "";
        int lines = clip.length() == 0 ? 0 : clip.split("\n", -1).length;
        out.print("Clipboard: " + clip.length() + " characters, " + lines + " lines");
    }

    // ================================================================
    // CLEAR
    // ================================================================

    private void handleClear() {
        pushHistory(getClipboard());
        setClipboard("");
        out.print("zclip: clipboard cleared.");
    }

    // ================================================================
    // SAVE
    // ================================================================

    private void handleSave(String[] parts) {
        if (parts.length < 3) {
            out.print("Usage: zclip save <file>");
            return;
        }
        String clip = getClipboard();
        if (clip == null || clip.length() == 0) {
            out.print("zclip: clipboard is empty.");
            return;
        }
        File f = resolveFile(parts[2]);
        if (f == null) {
            out.print("zclip: invalid path.");
            return;
        }
        FileOutputStream fos = null;
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            fos = new FileOutputStream(f);
            fos.write(clip.getBytes("UTF-8"));
            fos.flush();
            out.print("zclip: saved " + clip.length() + " chars to " + f.getAbsolutePath());
        } catch (Throwable t) {
            out.print("zclip: save failed: " + t.getMessage());
        } finally {
            if (fos != null) try { fos.close(); } catch (Throwable ignored) {}
        }
    }

    // ================================================================
    // LOAD
    // ================================================================

    private void handleLoad(String[] parts) {
        if (parts.length < 3) {
            out.print("Usage: zclip load <file>");
            return;
        }
        File f = resolveFile(parts[2]);
        if (f == null || !f.isFile()) {
            out.print("zclip: file not found: " + parts[2]);
            return;
        }
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(f), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            String text = sb.toString();
            pushHistory(getClipboard());
            setClipboard(text);
            out.print("zclip: loaded " + text.length() + " chars from " + f.getAbsolutePath());
        } catch (Throwable t) {
            out.print("zclip: load failed: " + t.getMessage());
        } finally {
            if (r != null) try { r.close(); } catch (Throwable ignored) {}
        }
    }

    // ================================================================
    // SEARCH
    // ================================================================

    private void handleSearch(String[] parts) {
        if (parts.length < 3) {
            out.print("Usage: zclip search <term>");
            return;
        }
        String clip = getClipboard();
        if (clip == null || clip.length() == 0) {
            out.print("zclip: clipboard is empty.");
            return;
        }
        String term = parts[2];
        String[] lines = clip.split("\n", -1);
        int matches = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(term)) {
                out.print("Line " + (i + 1) + ": " + lines[i]);
                matches++;
                if (matches >= 50) {
                    out.print("... (more matches truncated)");
                    break;
                }
            }
        }
        out.print("zclip: " + matches + " match(es) found.");
    }

    // ================================================================
    // REPLACE
    // ================================================================

    private void handleReplace(String[] parts) {
        if (parts.length < 4) {
            out.print("Usage: zclip replace <old> <new>");
            return;
        }
        String clip = getClipboard();
        if (clip == null || clip.length() == 0) {
            out.print("zclip: clipboard is empty.");
            return;
        }
        String oldStr = parts[2];
        String newStr = parts[3];
        int count = countOccurrences(clip, oldStr);
        if (count == 0) {
            out.print("zclip: '" + oldStr + "' not found.");
            return;
        }
        String replaced = clip.replace(oldStr, newStr);
        pushHistory(clip);
        setClipboard(replaced);
        out.print("zclip: replaced " + count + " occurrence(s).");
    }

    // ================================================================
    // TRANSFORM
    // ================================================================

    private void handleTransform(String op) {
        String clip = getClipboard();
        if (clip == null || clip.length() == 0) {
            out.print("zclip: clipboard is empty.");
            return;
        }
        String[] lines = clip.split("\n", -1);
        List<String> list = new ArrayList<String>(Arrays.asList(lines));

        if (op.equals("trim")) {
            List<String> trimmed = new ArrayList<String>();
            for (int i = 0; i < list.size(); i++) {
                String s = list.get(i).trim();
                if (s.length() > 0) trimmed.add(s);
            }
            list = trimmed;
        } else if (op.equals("reverse")) {
            Collections.reverse(list);
        } else if (op.equals("sort")) {
            Collections.sort(list);
        } else if (op.equals("uniq")) {
            List<String> uniq = new ArrayList<String>();
            String last = null;
            for (int i = 0; i < list.size(); i++) {
                String s = list.get(i);
                if (last == null || !last.equals(s)) {
                    uniq.add(s);
                }
                last = s;
            }
            list = uniq;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(list.get(i));
        }
        String result = sb.toString();
        pushHistory(clip);
        setClipboard(result);
        out.print("zclip: " + op + " applied (" + result.length() + " chars).");
    }

    // ================================================================
    // HISTORY
    // ================================================================

    private void handleHistory() {
        String hist = Prefs.getString(ctx, HISTORY_KEY, "");
        if (hist.length() == 0) {
            out.print("zclip: no history.");
            return;
        }
        String[] items = hist.split("\u0001", -1);
        int count = 0;
        for (int i = 0; i < items.length; i++) {
            String item = items[i];
            if (item.length() == 0) continue;
            count++;
            String preview = item.length() > 50
                    ? item.substring(0, 50) + "..."
                    : item;
            preview = preview.replace("\n", " ");
            out.print("[" + count + "] " + preview);
            if (count >= HISTORY_LIMIT) break;
        }
        if (count == 0) out.print("zclip: no history.");
    }

    // ================================================================
    // UNDO
    // ================================================================

    private void handleUndo() {
        String hist = Prefs.getString(ctx, HISTORY_KEY, "");
        if (hist.length() == 0) {
            out.print("zclip: no history to undo.");
            return;
        }
        String[] items = hist.split("\u0001", -1);
        // First item is most recent
        if (items.length == 0 || items[0].length() == 0) {
            out.print("zclip: no history to undo.");
            return;
        }
        String last = items[0];
        // Remove first item
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < items.length; i++) {
            if (items[i].length() == 0) continue;
            if (sb.length() > 0) sb.append('\u0001');
            sb.append(items[i]);
        }
        Prefs.putString(ctx, HISTORY_KEY, sb.toString());
        setClipboard(last);
        out.print("zclip: restored previous clipboard (" + last.length() + " chars).");
    }

    // ================================================================
    // Session buffer helpers
    // ================================================================

    private String getFullOutput() {
        SessionManager.Session s = mgr.active();
        if (s == null) return "";
        return s.buffer.toString();
    }

    private String getLine(int n) {
        String all = getFullOutput();
        if (all.length() == 0) return null;
        String[] lines = all.split("\n", -1);
        if (n < 1 || n > lines.length) return null;
        return lines[n - 1];
    }

    private String getLastLines(int n) {
        String all = getFullOutput();
        if (all.length() == 0) return "";
        String[] lines = all.split("\n", -1);
        int total = lines.length;
        if (n >= total) return all;
        StringBuilder sb = new StringBuilder();
        for (int i = total - n; i < total; i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private String getRange(int start, int end) {
        String all = getFullOutput();
        if (all.length() == 0) return "";
        String[] lines = all.split("\n", -1);
        if (start < 1) start = 1;
        if (end > lines.length) end = lines.length;
        if (start > end) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = start - 1; i < end; i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    // ================================================================
    // Clipboard
    // ================================================================

    private String getClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager)
                    ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return "";
            if (!cm.hasPrimaryClip()) return "";
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return "";
            CharSequence text = clip.getItemAt(0).coerceToText(ctx);
            return text == null ? "" : text.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private void setClipboard(String text) {
        try {
            ClipboardManager cm = (ClipboardManager)
                    ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return;
            cm.setPrimaryClip(ClipData.newPlainText("Zishu Terminal", text));
        } catch (Throwable ignored) {}
    }

    // ================================================================
    // History helpers
    // ================================================================

    private void pushHistory(String item) {
        if (item == null || item.length() == 0) return;
        String hist = Prefs.getString(ctx, HISTORY_KEY, "");
        // Add new item at front, remove duplicates, trim to limit
        String[] items = hist.length() == 0
                ? new String[0]
                : hist.split("\u0001", -1);
        List<String> list = new ArrayList<String>();
        list.add(item);
        for (int i = 0; i < items.length; i++) {
            if (items[i].length() == 0) continue;
            if (items[i].equals(item)) continue;
            list.add(items[i]);
            if (list.size() >= HISTORY_LIMIT) break;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append('\u0001');
            sb.append(list.get(i));
        }
        Prefs.putString(ctx, HISTORY_KEY, sb.toString());
    }

    // ================================================================
    // Helpers
    // ================================================================

    private File resolveFile(String path) {
        if (path == null || path.length() == 0) return null;
        // strip quotes
        if (path.length() >= 2) {
            char c0 = path.charAt(0);
            char c1 = path.charAt(path.length() - 1);
            if ((c0 == '"' && c1 == '"') || (c0 == '\'' && c1 == '\'')) {
                path = path.substring(1, path.length() - 1);
            }
        }
        try {
            ZishuEnv env = new ZishuEnv(ctx);
            String home = env.getHome().getAbsolutePath();
            path = path.replace("${HOME}", home);
            path = path.replace("$HOME", home);
            if (path.equals("~")) path = home;
            else if (path.startsWith("~/")) path = home + path.substring(1);
        } catch (Throwable ignored) {}

        File f = new File(path);
        if (!f.isAbsolute()) {
            SessionManager.Session s = mgr.active();
            String cwd = (s != null && s.cwd != null) ? s.cwd : ".";
            f = new File(cwd, path);
        }
        return f;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); }
        catch (Throwable t) { return def; }
    }

    private static int countOccurrences(String haystack, String needle) {
        if (needle == null || needle.length() == 0) return 0;
        int count = 0;
        int idx = 0;
        while (true) {
            int found = haystack.indexOf(needle, idx);
            if (found < 0) break;
            count++;
            idx = found + needle.length();
        }
        return count;
    }

    private void printHelp() {
        out.print("zclip " + "1.0.0" + " - Clipboard manager");
        out.print("");
        out.print("Copy:");
        out.print("  zclip copy                Copy full terminal output");
        out.print("  zclip copy <N>            Copy last N lines");
        out.print("  zclip copy line <N>       Copy line N");
        out.print("  zclip copy <start> <end>  Copy lines start..end");
        out.print("");
        out.print("Paste:");
        out.print("  zclip paste               Print clipboard");
        out.print("  zclip paste input         Insert clipboard into command");
        out.print("");
        out.print("Info:");
        out.print("  zclip show                Print clipboard");
        out.print("  zclip len                 Clipboard length");
        out.print("  zclip history             Last 10 clipboard items");
        out.print("  zclip undo                Restore previous clipboard");
        out.print("");
        out.print("File:");
        out.print("  zclip save <file>         Save clipboard to file");
        out.print("  zclip load <file>         Load file into clipboard");
        out.print("");
        out.print("Text:");
        out.print("  zclip search <term>       Search clipboard");
        out.print("  zclip replace <old> <new> Replace in clipboard");
        out.print("  zclip trim                Trim whitespace");
        out.print("  zclip reverse             Reverse lines");
        out.print("  zclip sort                Sort lines");
        out.print("  zclip uniq                Remove duplicate lines");
        out.print("");
        out.print("Other:");
        out.print("  zclip clear               Clear clipboard");
        out.print("  zclip help                Show this help");
    }
}