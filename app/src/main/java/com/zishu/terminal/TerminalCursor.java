package com.zishu.terminal;

import android.os.Handler;
import android.os.Looper;

/**
 * Blinking block cursor for the inline terminal.
 *
 * When selection is active, the cursor blinks are suppressed so the
 * selection highlight is not disturbed by repaints.
 */
public class TerminalCursor {

    private static final long BLINK_INTERVAL_MS = 500L;

    public static final String BLOCK = "\u2588";

    public interface Renderer {
        void onCursorTick();
    }

    private final Renderer renderer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean visible = true;
    private boolean running = false;
    private boolean paused = false;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (paused) {
                handler.postDelayed(this, BLINK_INTERVAL_MS);
                return;
            }
            visible = !visible;
            if (renderer != null) renderer.onCursorTick();
            handler.postDelayed(this, BLINK_INTERVAL_MS);
        }
    };

    public TerminalCursor(Renderer renderer) {
        this.renderer = renderer;
    }

    public void start() {
        if (running) return;
        running = true;
        visible = true;
        handler.postDelayed(tick, BLINK_INTERVAL_MS);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    public boolean isVisible() {
        return visible && !paused;
    }

    public void show() {
        visible = true;
        if (renderer != null) renderer.onCursorTick();
    }

    /** Pause / resume blinking (used during selection). */
    public void setPaused(boolean p) {
        paused = p;
        if (!p) visible = true;
    }
}