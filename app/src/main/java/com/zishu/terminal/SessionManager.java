package com.zishu.terminal;

import java.util.ArrayList;
import java.util.List;

public class SessionManager {

    public static class Session {
        public int id;
        public String name;
        public String cwd;
        public ShellProcess shell;
        public StringBuilder buffer = new StringBuilder();

        public Session(int id, String name, String cwd) {
            this.id = id;
            this.name = name;
            this.cwd = cwd;
        }
    }

    private static SessionManager instance;
    private final List<Session> sessions = new ArrayList<Session>();
    private int nextId = 1;
    private int activeId = -1;

    private SessionManager() {}

    public static synchronized SessionManager get() {
        if (instance == null) instance = new SessionManager();
        return instance;
    }

    public List<Session> all() {
        return sessions;
    }

    public Session byId(int id) {
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).id == id) return sessions.get(i);
        }
        return null;
    }

    public Session active() {
        Session s = byId(activeId);
        if (s == null && sessions.size() > 0) {
            s = sessions.get(0);
            activeId = s.id;
        }
        return s;
    }

    public int getActiveId() {
        return activeId;
    }

    public void setActive(int id) {
        activeId = id;
    }

    /**
     * Create a new session. If listener is non-null, a shell process is
     * started immediately and its output is routed to that listener.
     * If listener is null, the session is created without a shell; the caller
     * is responsible for attaching one later.
     */
    public Session create(String name, String cwd, ShellProcess.Listener listener) {
        int id = nextId++;
        String finalName = (name != null && name.length() > 0) ? name : ("Terminal " + id);
        Session s = new Session(id, finalName, cwd);
        if (listener != null) {
            s.shell = new ShellProcess(cwd, listener);
            s.shell.start();
        }
        sessions.add(s);
        if (activeId == -1) activeId = s.id;
        return s;
    }

    public void remove(int id) {
        Session s = byId(id);
        if (s == null) return;
        if (s.shell != null) s.shell.stop();
        sessions.remove(s);
        if (activeId == id) {
            activeId = sessions.size() > 0 ? sessions.get(0).id : -1;
        }
    }

    public void shutdownAll() {
        for (int i = 0; i < sessions.size(); i++) {
            Session s = sessions.get(i);
            if (s.shell != null) s.shell.stop();
        }
        sessions.clear();
        activeId = -1;
    }
}