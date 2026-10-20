package com.zishu.terminal;

/**
 * Package descriptor used by zpkg.
 * This is metadata only — no code from packages is executed automatically.
 */
public class PackageInfo {
    public String name = "";
    public String version = "";
    public String description = "";
    public String architecture = "";
    public String source = "";
    public String integrity = "";
    public long installedAt = 0L;
    public String[] files = new String[0];
    public String[] dependencies = new String[0];

    public PackageInfo() {}

    public PackageInfo(String name, String version, String description,
                       String architecture, String source) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.architecture = architecture;
        this.source = source;
    }
}