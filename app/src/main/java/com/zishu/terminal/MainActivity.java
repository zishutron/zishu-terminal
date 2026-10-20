package com.zishu.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/**
 * Main terminal activity with inline input (Termux-style).
 *
 * Rendering (v2 — batched):
 *   - Shell output is appended to a pending buffer.
 *   - A 50ms timer flushes the buffer to the TextView in one pass.
 *   - This avoids O(N^2) rendering during large outputs (cat, ls, etc.).
 *
 * Clipboard:
 *   `zclip` command handles copy/paste/history/save/load.
 */
public class MainActivity extends Activity {

    private ScrollView scrollOutput;
    private TextView txtOutput;
    private EditText inputCommand;
    private TextView sessionLabel;
    private LinearLayout topBar;
    private ImageView topLogo;

    private SessionManager mgr;
    private CommandHistory history;
    private ZishuCommands zishu;
    private ZishuEnv env;
    private TerminalKeyboardBar keyboardBar;
    private AnsiParser ansi;
    private TerminalCursor cursor;

    private int historyIndex = -1;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int fontSize;
    private int padPx;

    private String currentInput = "";
    private int cursorPos = 0;

    private boolean suppressTextWatcher = false;

    // ---- Batched rendering ----
    private static final long RENDER_DELAY_MS = 50L;
    private boolean renderScheduled = false;
    private boolean renderPending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        scrollOutput = (ScrollView) findViewById(R.id.scrollOutput);
        txtOutput = (TextView) findViewById(R.id.txtOutput);
        inputCommand = (EditText) findViewById(R.id.inputCommand);
        sessionLabel = (TextView) findViewById(R.id.sessionLabel);
        topBar = (LinearLayout) findViewById(R.id.topBar);
        topLogo = (ImageView) findViewById(R.id.topLogo);

        Button btnClear = (Button) findViewById(R.id.btnClearTop);
        Button btnMenu = (Button) findViewById(R.id.btnMenu);

        history = new CommandHistory(this);
        mgr = SessionManager.get();
        env = new ZishuEnv(this);
        env.ensureAll();
        ansi = new AnsiParser();

        fontSize = Prefs.getInt(this, Prefs.KEY_FONT_SIZE, Prefs.DEFAULT_FONT_SIZE);
        int padDp = Prefs.getInt(this, Prefs.KEY_PADDING, Prefs.DEFAULT_PADDING);
        padPx = (int) (padDp * getResources().getDisplayMetrics().density);

        applyThemeAndFont();

        if (!Prefs.getBool(this, "migrated_v5", false)) {
            try {
                env.ensureAll();
                new File(env.getPrefix(), "include").mkdirs();
                new File(env.getPrefix(), "libexec").mkdirs();
                new File(env.getPrefix(), "opt").mkdirs();
                new File(env.getVarDir(), "cache/zpkg").mkdirs();
                Prefs.putBool(this, "migrated_v5", true);
            } catch (Throwable ignored) {}
        }

        provisionMarkers();

        zishu = new ZishuCommands(this, new ZishuCommands.Host() {
            @Override public void print(String s) { appendLine(s); }
            @Override public void clearScreen() { txtOutput.setText(""); updatePrompt(); }
            @Override public String getCwd() { return getCurrentCwd(); }
            @Override public void setCwd(String cwd) {
                SessionManager.Session s = mgr.active();
                if (s != null) {
                    s.cwd = cwd;
                    if (s.shell != null) s.shell.cdTo(cwd);
                    updatePrompt();
                }
            }
            @Override public void showHistory() { startActivity(new Intent(MainActivity.this, HistoryActivity.class)); }
            @Override public void showSessions() { startActivity(new Intent(MainActivity.this, SessionsActivity.class)); }
            @Override public void showAbout() { startActivity(new Intent(MainActivity.this, AboutActivity.class)); }
            @Override public void showSettings() { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
            @Override public void showDiagnostics() { startActivity(new Intent(MainActivity.this, DiagnosticsActivity.class)); }
            @Override public void showPackages() { startActivity(new Intent(MainActivity.this, PackagesActivity.class)); }
            @Override public void showProjects() { startActivity(new Intent(MainActivity.this, ProjectsActivity.class)); }
            @Override public void showTools() { startActivity(new Intent(MainActivity.this, ToolsActivity.class)); }
            @Override public void showJobs() { startActivity(new Intent(MainActivity.this, JobsActivity.class)); }
            @Override public void showHelpTopic(String topic) { startActivity(new Intent(MainActivity.this, HelpActivity.class)); }
            @Override public void showServer() { startActivity(new Intent(MainActivity.this, ServerActivity.class)); }
            @Override public void runBackup() {
                new ZishuBackup(MainActivity.this, new ZishuBackup.Logger() {
                    @Override public void line(String s) { appendLineAsync(s); }
                }).backup();
            }
            @Override public void runRestore() {
                new ZishuBackup(MainActivity.this, new ZishuBackup.Logger() {
                    @Override public void line(String s) { appendLineAsync(s); }
                }).restore();
            }
            @Override public void openEditor(String path) {
                Intent i = new Intent(MainActivity.this, TextEditorActivity.class);
                i.putExtra("path", path);
                startActivity(i);
            }
            @Override public void runCommand(String command) {
                SessionManager.Session s = mgr.active();
                if (s == null || s.shell == null) { appendLine("[no shell]"); return; }
                appendLine("$ " + command);
                s.shell.write(command);
            }
            @Override public void startServer(String root, int port) { ServerActivity.getSharedServer().start(new File(root), port); }
            @Override public void stopServer() { ServerActivity.getSharedServer().stop(); }
            @Override public void restartServer() {
                ServerActivity.getSharedServer().stop();
                ServerActivity.getSharedServer().start(env.getHome(), 8080);
            }
            @Override public String getServerStatus() { return ServerActivity.getSharedServer().isRunning() ? "running" : "stopped"; }
            @Override public void createJob(String name, String command) { JobManager.get().create(name, command).status = "running"; }
        });

        if (mgr.all().isEmpty()) {
            String cwd = canonical(env.getHome());
            new File(cwd).mkdirs();
            createSessionInternal("Terminal 1", cwd, true);
        } else {
            attachActiveSession();
        }

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmClear(); }
        });
        btnMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TerminalMenu.show(MainActivity.this, v, new TerminalMenu.Callback() {
                    @Override public void onItem(int id) { handleMenuAction(id); }
                });
            }
        });

        inputCommand.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (suppressTextWatcher) return;
                currentInput = s == null ? "" : s.toString();
                int sel = inputCommand.getSelectionStart();
                if (sel < 0) sel = currentInput.length();
                if (sel > currentInput.length()) sel = currentInput.length();
                cursorPos = sel;
                if (cursor != null) cursor.show();
                redrawCurrentLine();
            }
        });

        inputCommand.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || actionId == EditorInfo.IME_ACTION_GO
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    submitInline();
                    return true;
                }
                return false;
            }
        });

        inputCommand.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { navigateHistoryInline(-1); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { navigateHistoryInline(1); return true; }
                if (keyCode == KeyEvent.KEYCODE_TAB) { autocompleteInline(); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (cursorPos > 0) { cursorPos--; try { inputCommand.setSelection(cursorPos); } catch (Throwable ignored) {} redrawCurrentLine(); }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (cursorPos < currentInput.length()) { cursorPos++; try { inputCommand.setSelection(cursorPos); } catch (Throwable ignored) {} redrawCurrentLine(); }
                    return true;
                }
                return false;
            }
        });

        txtOutput.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { focusInputAndShowKeyboard(); }
        });
        scrollOutput.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { focusInputAndShowKeyboard(); }
        });

        View kbView = findViewById(R.id.keyboardBarContainer);
        if (kbView != null) {
            keyboardBar = new TerminalKeyboardBar(this, kbView, inputCommand,
                    new TerminalKeyboardBar.Host() {
                        @Override public void onTab() { autocompleteInline(); }
                        @Override public void onHistoryUp() { navigateHistoryInline(-1); }
                        @Override public void onHistoryDown() { navigateHistoryInline(1); }
                        @Override public void onEscape() {
                            SessionManager.Session s = mgr.active();
                            if (s != null && s.shell != null && s.shell.isRunning()) {
                                s.shell.write("\u001b");
                            }
                        }
                    });
        }

        applyKeepAwake();

        cursor = new TerminalCursor(new TerminalCursor.Renderer() {
            @Override public void onCursorTick() { redrawCurrentLine(); }
        });
        cursor.start();

        SessionManager.Session s = mgr.active();
        if (s != null && s.buffer.length() == 0) {
            String banner = BannerRenderer.render(MainActivity.this);
            if (banner != null && banner.length() > 0) {
                String[] lines = banner.split("\n", -1);
                for (int i = 0; i < lines.length; i++) appendLine(lines[i]);
            }
            appendLine("");
        } else if (s != null) {
            redrawOutput();
            scrollToBottom();
        }
        updateSessionLabel();

        inputCommand.postDelayed(new Runnable() {
            @Override public void run() { focusInputAndShowKeyboard(); }
        }, 300);
    }

    // ================================================================
    // Markers
    // ================================================================

    private void provisionMarkers() {
        try {
            File binDir = env.getBinDir();
            if (!binDir.isDirectory()) binDir.mkdirs();
            File curlMarker = new File(binDir, "curl");
            if (!curlMarker.exists()) {
                writeMarker(curlMarker,
                        "#!/system/bin/sh\n"
                      + "echo \"curl: use this command inside Zishu Terminal\" >&2\n"
                      + "exit 2\n");
            }
            File zpMarker = new File(binDir, "zprotect");
            if (!zpMarker.exists()) {
                writeMarker(zpMarker,
                        "#!/system/bin/sh\n"
                      + "echo \"zprotect: use this command inside Zishu Terminal\" >&2\n"
                      + "exit 2\n");
            }
        } catch (Throwable ignored) {}
    }

    private void writeMarker(File f, String content) {
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
            fos.close();
            try { f.setReadable(true, false); } catch (Throwable ignored) {}
            try { f.setExecutable(true, false); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    // ================================================================
    // Theme
    // ================================================================

    private void applyThemeAndFont() {
        int theme = Prefs.getInt(this, Prefs.KEY_THEME, Prefs.DEFAULT_THEME);
        int bg, fg, barBg, barFg, divider;

        if (theme == Prefs.THEME_LIGHT) {
            bg = getResources().getColor(R.color.term_light_bg);
            fg = getResources().getColor(R.color.term_light_fg);
            barBg = Color.parseColor("#FFEAEAEA");
            barFg = Color.parseColor("#FF111111");
            divider = Color.parseColor("#FFCCCCCC");
        } else if (theme == Prefs.THEME_GREEN) {
            bg = getResources().getColor(R.color.term_green_bg);
            fg = getResources().getColor(R.color.term_green_fg);
            barBg = Color.parseColor("#FF0E2013");
            barFg = Color.parseColor("#FFE8FFE8");
            divider = Color.parseColor("#FF1E3A24");
        } else {
            bg = getResources().getColor(R.color.term_dark_bg);
            fg = getResources().getColor(R.color.term_dark_fg);
            barBg = Color.parseColor("#FF141414");
            barFg = Color.parseColor("#FFE8E8E8");
            divider = Color.parseColor("#FF2A2A2A");
        }

        View root = findViewById(android.R.id.content);
        if (root != null) root.setBackgroundColor(bg);

        if (topBar != null) topBar.setBackgroundColor(barBg);
        if (sessionLabel != null) sessionLabel.setTextColor(barFg);

        Button bClear = (Button) findViewById(R.id.btnClearTop);
        Button bMenu = (Button) findViewById(R.id.btnMenu);
        if (bClear != null) bClear.setTextColor(barFg);
        if (bMenu != null) bMenu.setTextColor(barFg);

        View dv = findViewById(R.id.topDivider);
        if (dv != null) dv.setBackgroundColor(divider);

        // Scroll view + its child container — fix for the "empty area
        // below text stays dark" bug.
        if (scrollOutput != null) {
            scrollOutput.setBackgroundColor(bg);
            if (scrollOutput.getChildCount() > 0) {
                View child = scrollOutput.getChildAt(0);
                if (child != null) child.setBackgroundColor(bg);
            }
        }

        if (txtOutput != null) {
            txtOutput.setBackgroundColor(bg);
            txtOutput.setTextColor(fg);
            txtOutput.setTextSize(fontSize);
            txtOutput.setPadding(padPx, padPx, padPx, padPx);
        }
    }

    private void applyKeepAwake() {
        boolean keep = Prefs.getBool(this, Prefs.KEY_KEEP_AWAKE, false);
        if (keep) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override protected void onResume() {
        super.onResume();
        fontSize = Prefs.getInt(this, Prefs.KEY_FONT_SIZE, Prefs.DEFAULT_FONT_SIZE);
        int padDp = Prefs.getInt(this, Prefs.KEY_PADDING, Prefs.DEFAULT_PADDING);
        padPx = (int) (padDp * getResources().getDisplayMetrics().density);
        applyThemeAndFont();
        applyKeepAwake();
        updateSessionLabel();
        redrawOutput();
    }

    @Override protected void onPause() {
        super.onPause();
        if (cursor != null) cursor.stop();
        // Flush any pending render so we don't lose output
        flushPendingRender();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (cursor != null) cursor.stop();
        mainHandler.removeCallbacksAndMessages(null);
    }

    // ================================================================
    // Rendering — batched
    // ================================================================

    /**
     * Schedule a render in the next RENDER_DELAY_MS window. Multiple
     * calls within that window collapse into a single render pass.
     */
    private void scheduleRender() {
        if (renderScheduled) return;
        renderScheduled = true;
        mainHandler.postDelayed(new Runnable() {
            @Override public void run() {
                renderScheduled = false;
                if (renderPending) {
                    renderPending = false;
                    redrawOutput();
                    scrollToBottomIfAtBottom();
                }
            }
        }, RENDER_DELAY_MS);
    }

    /** Force any pending render to happen immediately. */
    private void flushPendingRender() {
        if (renderPending) {
            renderPending = false;
            renderScheduled = false;
            mainHandler.removeCallbacksAndMessages(null);
            redrawOutput();
            scrollToBottomIfAtBottom();
        }
    }

    /**
     * Full rebuild of the TextView from the session buffer + current
     * inline line (prompt + command + cursor).
     */
    private void redrawOutput() {
        SessionManager.Session s = mgr.active();
        if (s == null) return;
        StringBuilder sb = new StringBuilder(s.buffer.toString());
        sb.append(buildCurrentLine());
        int fg = txtOutput.getCurrentTextColor();
        CharSequence styled;
        try {
            styled = ansi.parse(sb.toString(), fg);
        } catch (Throwable t) {
            styled = sb.toString();
        }
        txtOutput.setText(styled);
    }

    private void redrawCurrentLine() { redrawOutput(); }

    private String buildCurrentLine() {
        StringBuilder line = new StringBuilder();
        line.append(getPromptString()).append(' ');
        String input = currentInput;
        int pos = cursorPos;
        if (pos < 0) pos = 0;
        if (pos > input.length()) pos = input.length();
        String before = input.substring(0, pos);
        String after = input.substring(pos);
        line.append(before);
        if (cursor != null && cursor.isVisible()) line.append(TerminalCursor.BLOCK);
        line.append(after);
        if (after.length() == 0) line.append(' ');
        return line.toString();
    }

    private String getPromptString() {
        String cwd = getCurrentCwd();
        String shown = cwd;
        try {
            String home = canonical(env.getHome());
            if (cwd.equals(home)) shown = "~";
            else if (cwd.startsWith(home + "/")) shown = "~" + cwd.substring(home.length());
        } catch (Throwable ignored) {}
        if (shown.length() > 28) {
            String[] parts = shown.split("/");
            if (parts.length > 2) shown = ".../" + parts[parts.length - 2] + "/" + parts[parts.length - 1];
            else if (parts.length == 2) shown = ".../" + parts[1];
        }
        String fmt = Prefs.getString(this, "prompt_format", null);
        if (fmt == null || fmt.length() == 0) fmt = "zishu@android:{cwd} $";
        fmt = fmt.replace("{user}", "zishu");
        fmt = fmt.replace("{cwd}", shown);
        fmt = fmt.replace("{time}", zishu.timestamp());
        return fmt;
    }

    private void updatePrompt() { redrawCurrentLine(); }

    // ================================================================
    // Inline input
    // ================================================================

    private void submitInline() {
        String line = currentInput.trim();
        SessionManager.Session s = mgr.active();
        if (s != null) {
            s.buffer.append(getPromptString()).append(' ').append(currentInput).append('\n');
            if (s.buffer.length() > 200000) s.buffer.delete(0, 100000);
        }
        currentInput = "";
        cursorPos = 0;
        suppressTextWatcher = true;
        try { inputCommand.setText(""); } catch (Throwable ignored) {}
        suppressTextWatcher = false;
        if (line.length() == 0) { redrawOutput(); scrollToBottom(); focusInputAndShowKeyboard(); return; }
        history.add(line);
        historyIndex = -1;
        if (handleBuiltins(line)) { redrawOutput(); scrollToBottom(); focusInputAndShowKeyboard(); return; }
        if (s == null) { appendLine("[no active session]"); focusInputAndShowKeyboard(); return; }
        if (s.shell == null || !s.shell.isRunning()) {
            s.shell = new ShellProcess(s.cwd, new ShellListener());
            if (!s.shell.start()) { appendLine("[failed to start shell]"); focusInputAndShowKeyboard(); return; }
            sendEnvSnippet(s.shell);
        }
        if (line.equals("cd") || line.startsWith("cd ")) {
            String target = line.equals("cd") ? "~" : line.substring(3).trim();
            target = expandVars(target);
            String resolved = resolveCd(target);
            if (resolved != null) {
                s.cwd = resolved;
                s.shell.cdTo(resolved);
                s.shell.write("cd \"" + resolved + "\"");
                redrawOutput(); scrollToBottom(); focusInputAndShowKeyboard();
                return;
            }
        }
        s.shell.write(line);
        focusInputAndShowKeyboard();
    }

    private void navigateHistoryInline(int direction) {
        if (history.size() == 0) return;
        if (historyIndex == -1) historyIndex = 0;
        else {
            historyIndex += direction;
            if (historyIndex < 0) historyIndex = 0;
            if (historyIndex >= history.size()) { historyIndex = -1; setInlineInput(""); return; }
        }
        String cmd = history.get(historyIndex);
        if (cmd != null) setInlineInput(cmd);
    }

    private void autocompleteInline() {
        String suggestion = AutocompleteEngine.suggest(currentInput, getCurrentCwd());
        if (suggestion != null && !suggestion.equals(currentInput.trim())) setInlineInput(suggestion);
    }

    private void setInlineInput(String text) {
        if (text == null) text = "";
        currentInput = text;
        cursorPos = text.length();
        suppressTextWatcher = true;
        try { inputCommand.setText(text); inputCommand.setSelection(text.length()); }
        catch (Throwable ignored) {}
        suppressTextWatcher = false;
        if (cursor != null) cursor.show();
        redrawCurrentLine();
    }

    // ================================================================
    // Sessions
    // ================================================================

    private void createSessionInternal(String name, String cwd, boolean makeActive) {
        SessionManager.Session s = mgr.create(name, cwd, new ShellListener());
        if (makeActive) mgr.setActive(s.id);
        sendEnvSnippet(s.shell);
        updateSessionLabel();
    }

    private void attachActiveSession() {
        SessionManager.Session s = mgr.active();
        if (s == null) return;
        if (s.shell == null || !s.shell.isRunning()) {
            s.shell = new ShellProcess(s.cwd, new ShellListener());
            s.shell.start();
            sendEnvSnippet(s.shell);
        }
        updateSessionLabel();
    }

    private void sendEnvSnippet(final ShellProcess shell) {
        if (shell == null) return;
        final String snippet = env.exportSnippet();
        try { shell.write(snippet); } catch (Throwable ignored) {}
        mainHandler.postDelayed(new Runnable() {
            @Override public void run() {
                try { if (shell.isRunning()) shell.write(snippet); } catch (Throwable ignored) {}
            }
        }, 150);
        mainHandler.postDelayed(new Runnable() {
            @Override public void run() { runZprofile(shell); }
        }, 400);
    }

    private void runZprofile(ShellProcess shell) {
        if (shell == null || !shell.isRunning()) return;
        try {
            File zprofile = new File(env.getHome(), ".zprofile");
            if (!zprofile.isFile()) createDefaultZprofile(zprofile);
            if (zprofile.isFile()) shell.write(". \"" + zprofile.getAbsolutePath() + "\"");
        } catch (Throwable ignored) {}
    }

    private void createDefaultZprofile(File zprofile) {
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream(zprofile);
            String content =
                    "# Zishu Terminal - Startup Profile\n"
                  + "# This file runs every time a new shell session starts.\n"
                  + "\n"
                  + "export ZISHU_USER=zishu\n"
                  + "export ZISHU_HOME=$HOME\n"
                  + "export ZISHU_PREFIX=$PREFIX\n";
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
            fos.close();
            try { zprofile.setReadable(true, false); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private class ShellListener implements ShellProcess.Listener {
        @Override public void onOutput(String line) { appendLineAsync(line); }
        @Override public void onError(String line) { appendLineAsync(line); }
        @Override public void onExit(int code) { appendLineAsync("[session exited with code " + code + "]"); }
    }

    private void appendLineAsync(final String line) {
        mainHandler.post(new Runnable() {
            @Override public void run() { appendLine(line); }
        });
    }

    /**
     * Append a line to the session buffer. Does NOT render immediately —
     * sets renderPending and schedules a batched render 50ms later.
     */
    private void appendLine(String line) {
        SessionManager.Session s = mgr.active();
        String out = line == null ? "" : line;
        if (Prefs.getBool(MainActivity.this, Prefs.KEY_TIMESTAMPS, false)) {
            out = "[" + zishu.timestamp() + "] " + out;
        }
        if (s != null) {
            s.buffer.append(out).append('\n');
            if (s.buffer.length() > 200000) s.buffer.delete(0, 100000);
        }
        renderPending = true;
        scheduleRender();
    }

    private void scrollToBottom() {
        scrollOutput.post(new Runnable() {
            @Override public void run() { scrollOutput.fullScroll(View.FOCUS_DOWN); }
        });
    }

    private void scrollToBottomIfAtBottom() {
        scrollOutput.post(new Runnable() {
            @Override public void run() {
                View child = scrollOutput.getChildAt(0);
                if (child == null) return;
                int childBottom = child.getBottom();
                int viewBottom = scrollOutput.getScrollY() + scrollOutput.getHeight();
                int distanceFromBottom = childBottom - viewBottom;
                if (distanceFromBottom <= 150) scrollOutput.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    // ================================================================
    // Path helpers
    // ================================================================

    private String canonical(File f) {
        if (f == null) return null;
        try { return f.getCanonicalPath(); } catch (Throwable ignored) { return f.getAbsolutePath(); }
    }

    private String getCurrentCwd() {
        SessionManager.Session s = mgr.active();
        if (s != null && s.cwd != null) {
            try { return new File(s.cwd).getCanonicalPath(); } catch (Throwable ignored) { return s.cwd; }
        }
        return canonical(env.getHome());
    }

    private void updateSessionLabel() {
        SessionManager.Session s = mgr.active();
        if (s != null) sessionLabel.setText(s.name);
    }

    private String expandVars(String target) {
        if (target == null) return null;
        String t = target;
        try {
            String home = canonical(env.getHome());
            String prefix = canonical(env.getPrefix());
            String tmp = canonical(env.getTmp());
            t = t.replace("${HOME}", home); t = t.replace("${PREFIX}", prefix); t = t.replace("${TMPDIR}", tmp);
            t = t.replace("$HOME", home); t = t.replace("$PREFIX", prefix); t = t.replace("$TMPDIR", tmp);
        } catch (Throwable ignored) {}
        return t;
    }

    private String resolveCd(String target) {
        String cwd = getCurrentCwd();
        if (target == null || target.length() == 0 || target.equals("~")) return canonical(env.getHome());
        File f;
        if (target.startsWith("/")) f = new File(target);
        else f = new File(cwd, target);
        try {
            String canonicalPath = f.getCanonicalPath();
            if (new File(canonicalPath).isDirectory()) return canonicalPath;
        } catch (Throwable ignored) {}
        return null;
    }

    // ================================================================
    // Builtins
    // ================================================================

    private boolean handleBuiltins(String line) {
        if (zishu.handle(line)) return true;

        try {
            if (line != null && (line.trim().equals("zclip")
                    || line.trim().startsWith("zclip "))) {
                ZclipCommand zclip = new ZclipCommand(
                        MainActivity.this,
                        new ZclipCommand.Output() {
                            @Override public void print(String s) {
                                appendLineAsync(s);
                            }
                        },
                        new ZclipCommand.InputHandler() {
                            @Override public void setInput(final String text) {
                                mainHandler.post(new Runnable() {
                                    @Override public void run() {
                                        setInlineInput(text);
                                        focusInputAndShowKeyboard();
                                    }
                                });
                            }
                        });
                if (zclip.handle(line)) return true;
            }
        } catch (Throwable t) {
            appendLineAsync("zclip: internal error: " + t.getMessage());
            return true;
        }

        try {
            String cwd = getCurrentCwd();
            if (CurlBridge.tryHandle(MainActivity.this, line, cwd, new CurlBridge.Output() {
                @Override public void line(String txt) { appendLineAsync(txt); }
            })) return true;
        } catch (Throwable t) { appendLineAsync("curl: internal error: " + t.getMessage()); return true; }

        try {
            if (line != null && line.trim().startsWith("zprotect")) {
                String cwd = getCurrentCwd();
                ZishuProtectCommand pc = new ZishuProtectCommand(MainActivity.this, new ZishuProtectCommand.Output() {
                    @Override public void print(String s) { appendLineAsync(s); }
                });
                if (pc.handle(line, cwd)) return true;
            }
        } catch (Throwable t) { appendLineAsync("zprotect: internal error: " + t.getMessage()); return true; }

        try {
            if (line != null && (line.trim().equals("zgit") || line.trim().startsWith("zgit "))) {
                ZgitClone runner = new ZgitClone(new ZgitClone.LineWriter() {
                    @Override public void writeLine(String s) { appendLineAsync(s); }
                }, new File(getCurrentCwd()), env);
                if (runner.tryHandle(line)) return true;
            }
        } catch (Throwable t) { appendLineAsync("zgit: internal error: " + t.getMessage()); return true; }

        if (line.equals("clear")) {
            SessionManager.Session s = mgr.active();
            if (s != null) s.buffer.setLength(0);
            txtOutput.setText("");
            redrawOutput();
            return true;
        }
        if (line.equals("exit")) { finish(); return true; }
        return false;
    }

    // ================================================================
    // Clear
    // ================================================================

    private void confirmClear() {
        if (!Prefs.getBool(this, Prefs.KEY_CONFIRM_CLEAR, false)) { doClear(); return; }
        new AlertDialog.Builder(this)
                .setTitle("Clear Terminal")
                .setMessage("Clear the terminal output?")
                .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { doClear(); }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void doClear() {
        SessionManager.Session s = mgr.active();
        if (s != null) s.buffer.setLength(0);
        txtOutput.setText("");
        redrawOutput();
    }

    // ================================================================
    // Menu
    // ================================================================

    private void handleMenuAction(int id) {
        if (id == TerminalMenu.ITEM_NEW_SESSION) {
            String cwd = canonical(env.getHome());
            new File(cwd).mkdirs();
            int n = mgr.all().size() + 1;
            createSessionInternal("Terminal " + n, cwd, true);
            SessionManager.Session s = mgr.active();
            if (s != null) { redrawOutput(); scrollToBottom(); updateSessionLabel(); }
        } else if (id == TerminalMenu.ITEM_SESSIONS) startActivity(new Intent(this, SessionsActivity.class));
        else if (id == TerminalMenu.ITEM_FILES) startActivity(new Intent(this, FileManagerActivity.class));
        else if (id == TerminalMenu.ITEM_EDITOR) {
            Intent i = new Intent(this, TextEditorActivity.class);
            i.putExtra("path", "");
            startActivity(i);
        }
        else if (id == TerminalMenu.ITEM_HISTORY) startActivity(new Intent(this, HistoryActivity.class));
        else if (id == TerminalMenu.ITEM_SYSINFO) startActivity(new Intent(this, SystemInfoActivity.class));
        else if (id == TerminalMenu.ITEM_SETTINGS) startActivity(new Intent(this, SettingsActivity.class));
        else if (id == TerminalMenu.ITEM_ABOUT) startActivity(new Intent(this, AboutActivity.class));
        else if (id == TerminalMenu.ITEM_RESTART) restartActiveShell();
        else if (id == TerminalMenu.ITEM_CLEAR) confirmClear();
        else if (id == TerminalMenu.ITEM_EXIT) finish();
        else if (id == TerminalMenu.ITEM_DIAGNOSTICS) startActivity(new Intent(this, DiagnosticsActivity.class));
        else if (id == TerminalMenu.ITEM_PACKAGES) startActivity(new Intent(this, PackagesActivity.class));
        else if (id == TerminalMenu.ITEM_PROJECTS) startActivity(new Intent(this, ProjectsActivity.class));
        else if (id == TerminalMenu.ITEM_BACKUP) showBackupDialog();
        else if (id == TerminalMenu.ITEM_TOOLS) startActivity(new Intent(this, ToolsActivity.class));
        else if (id == TerminalMenu.ITEM_JOBS) startActivity(new Intent(this, JobsActivity.class));
        else if (id == TerminalMenu.ITEM_HELP) startActivity(new Intent(this, HelpActivity.class));
        else if (id == TerminalMenu.ITEM_SERVER) startActivity(new Intent(this, ServerActivity.class));
    }

    private void showBackupDialog() {
        final String[] opts = {"Backup", "Restore", "Reset settings"};
        new AlertDialog.Builder(this)
                .setTitle("Backup / Restore")
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        ZishuBackup b = new ZishuBackup(MainActivity.this, new ZishuBackup.Logger() {
                            @Override public void line(String s) { appendLineAsync(s); }
                        });
                        if (which == 0) b.backup();
                        else if (which == 1) b.restore();
                        else {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Reset settings?")
                                    .setMessage("This clears preferences only. Files are not deleted.")
                                    .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                                        @Override public void onClick(DialogInterface dd, int w) {
                                            b.reset();
                                            appendLineAsync("[settings reset]");
                                        }
                                    })
                                    .setNegativeButton("Cancel", null).show();
                        }
                    }
                }).show();
    }

    private void restartActiveShell() {
        SessionManager.Session s = mgr.active();
        if (s == null) return;
        if (s.shell != null) s.shell.stop();
        s.shell = new ShellProcess(s.cwd, new ShellListener());
        s.shell.start();
        sendEnvSnippet(s.shell);
        appendLine("[shell restarted]");
    }

    private void focusInputAndShowKeyboard() {
        if (inputCommand == null) return;
        inputCommand.requestFocus();
        inputCommand.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(inputCommand, InputMethodManager.SHOW_IMPLICIT);
                } catch (Throwable ignored) {}
            }
        }, 50);
    }

    @Override
    public void onBackPressed() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && imm.isActive(inputCommand)) {
                imm.hideSoftInputFromWindow(inputCommand.getWindowToken(), 0);
                return;
            }
        } catch (Throwable ignored) {}
        super.onBackPressed();
    }
}
