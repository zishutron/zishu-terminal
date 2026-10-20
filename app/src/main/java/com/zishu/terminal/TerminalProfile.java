package com.zishu.terminal;

/**
 * A named terminal profile. Persisted as strings in SharedPreferences
 * under key profile.<name>.<field>.
 */
public class TerminalProfile {
    public String name = "Default";
    public int fontSize = 14;
    public int theme = Prefs.THEME_DARK;
    public String prompt = "zishu@android";
    public int cursorStyle = 0;
    public boolean cursorBlink = true;
    public boolean keyboardBar = true;
    public String extraPath = "";
    public String startDir = "";

    public TerminalProfile() {}

    public TerminalProfile(String name) { this.name = name; }
}