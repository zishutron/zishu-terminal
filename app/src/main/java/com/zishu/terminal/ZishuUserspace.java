package com.zishu.terminal;

import android.content.Context;

import java.io.File;

/**
 * Thin wrapper around ZishuEnv that owns initialization and integrity
 * checks of the userspace layout. Kept separate so future upgrades can
 * extend it without touching ZishuEnv.
 */
public class ZishuUserspace {

    private final ZishuEnv env;

    public ZishuUserspace(Context ctx) {
        this.env = new ZishuEnv(ctx);
    }

    public ZishuEnv env() { return env; }

    public void init() {
        env.ensureAll();
    }

    public static class CheckResult {
        public boolean ok;
        public String message;
        public CheckResult(boolean ok, String message) {
            this.ok = ok; this.message = message;
        }
    }

    public CheckResult check() {
        try {
            env.ensureAll();
            File[] required = new File[]{
                    env.getPrefix(), env.getBinDir(), env.getHome(),
                    env.getTmp(), env.getVarDir()
            };
            for (int i = 0; i < required.length; i++) {
                if (!required[i].isDirectory()) {
                    return new CheckResult(false,
                            "Missing: " + required[i].getAbsolutePath());
                }
            }
            return new CheckResult(true, env.getPrefix().getAbsolutePath());
        } catch (Throwable t) {
            return new CheckResult(false, t.getMessage());
        }
    }
}