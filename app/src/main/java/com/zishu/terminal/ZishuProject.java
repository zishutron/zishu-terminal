package com.zishu.terminal;

/**
 * Data holder for a project (kept separate from ProjectManager so future
 * versions can carry richer metadata without breaking the manager).
 */
public class ZishuProject {
    public String name;
    public String path;
    public long createdAt;
    public long modifiedAt;

    public ZishuProject() {}
    public ZishuProject(String name, String path, long createdAt, long modifiedAt) {
        this.name = name;
        this.path = path;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
    }
}