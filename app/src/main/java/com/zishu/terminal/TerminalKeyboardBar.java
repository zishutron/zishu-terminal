package com.zishu.terminal;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

/**
 * Wires up the quick-access keyboard bar in activity_terminal.xml.
 * Handles ESC/TAB/CTRL/ALT/arrows and quick symbols.
 *
 * Special keys:
 *   - TAB  → calls host.onTab() (autocomplete)
 *   - UP   → host.onHistoryUp()
 *   - DOWN → host.onHistoryDown()
 *   - LEFT / RIGHT → move cursor in EditText
 *   - ESC  → sends ESC control byte to shell (via host.onEscape())
 *   - CTRL → modifier state; next letter becomes control char
 *   - ALT  → modifier state (reserved)
 *   - symbols → insert into EditText at cursor
 */
public class TerminalKeyboardBar {

    public interface Host {
        void onTab();
        void onHistoryUp();
        void onHistoryDown();
        void onEscape();
    }

    private boolean ctrl = false;
    private boolean alt = false;
    private final Activity activity;
    private final EditText input;
    private final Host host;
    private Button ctrlBtn, altBtn;

    public TerminalKeyboardBar(Activity a, View barView, EditText input, Host host) {
        this.activity = a;
        this.input = input;
        this.host = host;
        wire(barView);
    }

    private void wire(View v) {
        ctrlBtn = (Button) v.findViewById(R.id.kbCtrl);
        altBtn  = (Button) v.findViewById(R.id.kbAlt);

        setClick(v, R.id.kbEsc, new Runnable() {
            @Override public void run() {
                if (host != null) host.onEscape();
            }
        });
        setClick(v, R.id.kbTab, new Runnable() {
            @Override public void run() {
                if (host != null) host.onTab();
            }
        });
        setClick(v, R.id.kbUp, new Runnable() {
            @Override public void run() {
                if (host != null) host.onHistoryUp();
            }
        });
        setClick(v, R.id.kbDown, new Runnable() {
            @Override public void run() {
                if (host != null) host.onHistoryDown();
            }
        });
        setClick(v, R.id.kbLeft, new Runnable() {
            @Override public void run() { moveCursor(-1); }
        });
        setClick(v, R.id.kbRight, new Runnable() {
            @Override public void run() { moveCursor(1); }
        });
        setSymbol(v, R.id.kbSlash, "/");
        setSymbol(v, R.id.kbTilde, "~");
        setSymbol(v, R.id.kbDollar, "$");
        setSymbol(v, R.id.kbPipe, "|");
        setSymbol(v, R.id.kbDash, "-");
        setSymbol(v, R.id.kbDot, ".");
        setSymbol(v, R.id.kbStar, "*");
        setSymbol(v, R.id.kbQuote, "\"");

        if (ctrlBtn != null) {
            ctrlBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    ctrl = !ctrl;
                    ctrlBtn.setBackgroundColor(ctrl ? 0xFF2A5A2A : 0xFF1A1A1A);
                }
            });
        }
        if (altBtn != null) {
            altBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    alt = !alt;
                    altBtn.setBackgroundColor(alt ? 0xFF2A5A2A : 0xFF1A1A1A);
                }
            });
        }
    }

    /**
     * Called from MainActivity's TextWatcher to interpret a typed character
     * as a ctrl-combination. Returns true if the character was consumed.
     */
    public boolean handleCtrl(CharSequence text) {
        if (!ctrl || text == null || text.length() == 0) return false;
        char c = Character.toLowerCase(text.charAt(0));
        if (c >= 'a' && c <= 'z') {
            int code = (c - 'a') + 1;
            // Send control char to shell via input (MainActivity intercepts)
            try {
                int start = input.getSelectionStart();
                input.getText().insert(start, String.valueOf((char) code));
            } catch (Throwable ignored) {}
            ctrl = false;
            if (ctrlBtn != null) ctrlBtn.setBackgroundColor(0xFF1A1A1A);
            return true;
        }
        return false;
    }

    private void moveCursor(int delta) {
        try {
            int pos = input.getSelectionStart() + delta;
            if (pos < 0) pos = 0;
            if (pos > input.getText().length()) pos = input.getText().length();
            input.setSelection(pos);
        } catch (Throwable ignored) {}
    }

    private void setSymbol(View parent, int id, final String s) {
        View btn = parent.findViewById(id);
        if (btn == null) return;
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    int start = input.getSelectionStart();
                    input.getText().insert(start, s);
                } catch (Throwable ignored) {}
            }
        });
    }

    private void setClick(View parent, int id, final Runnable r) {
        View btn = parent.findViewById(id);
        if (btn == null) return;
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { r.run(); }
        });
    }
}
