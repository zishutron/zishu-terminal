package com.zishu.terminal;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Side dropdown menu shown when the user taps Menu.
 * Wrapped in a ScrollView so all items remain reachable on small screens.
 */
public class TerminalMenu {

    public interface Callback { void onItem(int id); }

    public static final int ITEM_NEW_SESSION  = 1;
    public static final int ITEM_SESSIONS     = 2;
    public static final int ITEM_FILES        = 3;
    public static final int ITEM_EDITOR       = 4;
    public static final int ITEM_HISTORY      = 5;
    public static final int ITEM_SYSINFO      = 6;
    public static final int ITEM_SETTINGS     = 7;
    public static final int ITEM_ABOUT        = 8;
    public static final int ITEM_RESTART      = 9;
    public static final int ITEM_CLEAR        = 10;
    public static final int ITEM_EXIT         = 11;
    public static final int ITEM_DIAGNOSTICS  = 12;
    public static final int ITEM_PACKAGES     = 13;
    public static final int ITEM_PROJECTS     = 14;
    public static final int ITEM_BACKUP       = 15;
    public static final int ITEM_TOOLS        = 16;
    public static final int ITEM_JOBS         = 17;
    public static final int ITEM_HELP         = 18;
    public static final int ITEM_SERVER       = 19;

    private static final String[] LABELS = {
            "New Session",
            "Sessions",
            "Files",
            "Text Editor",
            "Command History",
            "System Info",
            "Settings",
            "About",
            "Restart Shell",
            "Clear Terminal",
            "Diagnostics",
            "Packages (zpkg)",
            "Projects",
            "Backup / Restore",
            "Tools",
            "Jobs",
            "Help",
            "Server",
            "Exit"
    };

    private static final int[] IDS = {
            ITEM_NEW_SESSION,
            ITEM_SESSIONS,
            ITEM_FILES,
            ITEM_EDITOR,
            ITEM_HISTORY,
            ITEM_SYSINFO,
            ITEM_SETTINGS,
            ITEM_ABOUT,
            ITEM_RESTART,
            ITEM_CLEAR,
            ITEM_DIAGNOSTICS,
            ITEM_PACKAGES,
            ITEM_PROJECTS,
            ITEM_BACKUP,
            ITEM_TOOLS,
            ITEM_JOBS,
            ITEM_HELP,
            ITEM_SERVER,
            ITEM_EXIT
    };

    public static void show(final Activity activity, View anchor, final Callback cb) {
        // ---- Inner vertical container with menu items ----
        final LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.parseColor("#FF141414"));
        container.setPadding(dp(activity, 4), dp(activity, 6),
                dp(activity, 4), dp(activity, 6));
        container.setMinimumWidth(dp(activity, 220));

        for (int i = 0; i < LABELS.length; i++) {
            final int id = IDS[i];
            TextView tv = new TextView(activity);
            tv.setText(LABELS[i]);
            tv.setTextColor(Color.parseColor("#FFE8E8E8"));
            tv.setTextSize(14);
            tv.setPadding(dp(activity, 14), dp(activity, 12),
                    dp(activity, 14), dp(activity, 12));
            tv.setSingleLine(true);
            tv.setBackgroundColor(Color.parseColor("#FF141414"));
            tv.setClickable(true);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    // dismiss popup when item is clicked
                    View root = v.getRootView();
                    if (root != null && root.getParent() instanceof PopupWindow) {
                        ((PopupWindow) root.getParent()).dismiss();
                    }
                    // fallback: we still call the callback
                    if (cb != null) cb.onItem(id);
                }
            });
            tv.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, android.view.MotionEvent event) {
                    int action = event.getAction();
                    if (action == android.view.MotionEvent.ACTION_DOWN) {
                        v.setBackgroundColor(Color.parseColor("#FF2A3A2A"));
                    } else if (action == android.view.MotionEvent.ACTION_UP
                            || action == android.view.MotionEvent.ACTION_CANCEL) {
                        v.setBackgroundColor(Color.parseColor("#FF141414"));
                    }
                    return false;
                }
            });
            container.addView(tv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            if (i < LABELS.length - 1) {
                View div = new View(activity);
                div.setBackgroundColor(Color.parseColor("#FF222222"));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                lp.leftMargin = dp(activity, 8);
                lp.rightMargin = dp(activity, 8);
                container.addView(div, lp);
            }
        }

        // ---- Wrap the container in a ScrollView ----
        final ScrollView scroller = new ScrollView(activity);
        scroller.setBackgroundColor(Color.parseColor("#FF141414"));
        scroller.setFillViewport(false);
        scroller.addView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- Compute an appropriate max height ----
        // Reserve room for the top bar (~56dp) and some margin (~32dp) at bottom.
        int screenH = activity.getResources().getDisplayMetrics().heightPixels;
        int maxH = screenH - dp(activity, 100);
        if (maxH < dp(activity, 200)) maxH = dp(activity, 200);

        // ---- PopupWindow wrapping the ScrollView ----
        final PopupWindow popup = new PopupWindow(scroller,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            popup.setElevation(dp(activity, 8));
        }

        // ---- Measure and clamp height ----
        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = container.getMeasuredWidth();
        int popupHeight = container.getMeasuredHeight();

        int finalHeight = popupHeight > maxH ? maxH : popupHeight;
        popup.setHeight(finalHeight);

        // ---- Compute X so menu right-aligns to the anchor ----
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int anchorRight = loc[0] + anchor.getWidth();
        int xoff = anchorRight - popupWidth;
        if (xoff < 0) xoff = 0;

        popup.showAsDropDown(anchor, xoff - anchor.getLeft(), 4);
    }

    private static int dp(Activity a, int v) {
        return (int) (v * a.getResources().getDisplayMetrics().density);
    }
}
