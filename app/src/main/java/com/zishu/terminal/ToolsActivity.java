package com.zishu.terminal;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class ToolsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setPadding(24, 24, 24, 24);
        tv.setTextSize(13);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextColor(0xFFE8E8E8);
        sv.setBackgroundColor(0xFF0B0F0C);
        sv.addView(tv);
        setContentView(sv);

        ZishuEnv env = new ZishuEnv(this);
        StringBuilder sb = new StringBuilder();
        sb.append("Developer Tools — Real Detection\n\n");
        ToolchainStatus.Status[] all = ToolchainStatus.all(env);
        for (int i = 0; i < all.length; i++) {
            ToolchainStatus.Status s = all[i];
            String state = s.installed ? "[OK]   " : "[MISS] ";
            sb.append(state).append(s.displayName);
            if (s.installed && s.path != null) sb.append("  ").append(s.path);
            else sb.append("  ").append(s.note);
            sb.append('\n');
        }
        sb.append("\nInstalled tools use the real executable.\n");
        sb.append("Missing tools can be added through zpkg.");
        tv.setText(sb.toString());
    }
}