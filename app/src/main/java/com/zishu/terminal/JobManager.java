package com.zishu.terminal;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks Zishu-managed jobs (started via zrun / zbuild / zserver etc.)
 * so users can see, stop, and inspect them. This is not a system-wide
 * process killer — it only manages processes the app started.
 */
public class JobManager {

    public static class Job {
        public int id;
        public String name;
        public String command;
        public long startedAt;
        public volatile String status = "pending"; // pending/running/completed/failed/cancelled
        public int exitCode = -1;
        public volatile Process process;
    }

    private static JobManager instance;
    private final List<Job> jobs = new ArrayList<Job>();
    private int nextId = 1;

    public static synchronized JobManager get() {
        if (instance == null) instance = new JobManager();
        return instance;
    }

    public Job create(String name, String command) {
        Job j = new Job();
        j.id = nextId++;
        j.name = name;
        j.command = command;
        j.startedAt = System.currentTimeMillis();
        jobs.add(j);
        return j;
    }

    public void remove(int id) {
        Job j = byId(id);
        if (j != null) jobs.remove(j);
    }

    public Job byId(int id) {
        for (int i = 0; i < jobs.size(); i++)
            if (jobs.get(i).id == id) return jobs.get(i);
        return null;
    }

    public List<Job> all() { return new ArrayList<Job>(jobs); }

    public void stop(int id) {
        Job j = byId(id);
        if (j == null) return;
        j.status = "cancelled";
        if (j.process != null) {
            try { j.process.destroy(); } catch (Throwable ignored) {}
        }
    }
}