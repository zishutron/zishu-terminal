package com.zishu.terminal;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class HelpActivity extends Activity {

    private static final String[] TOPICS = {
            "Getting Started",
            "Terminal",
            "Userspace",
            "Packages (zpkg)",
            "Projects",
            "Editor",
            "Server",
            "Diagnostics",
            "Security",
            "Troubleshooting"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ListView lv = new ListView(this);
        lv.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, TOPICS));
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                showTopic(TOPICS[pos]);
            }
        });
        setContentView(lv);
    }

    private void showTopic(String topic) {
        StringBuilder sb = new StringBuilder();
        if (topic.equals("Getting Started")) {
            sb.append("Zishu Terminal v5.0\n\n");
            sb.append("1. Tap the input bar at the bottom.\n");
            sb.append("2. Type a command and press Run.\n");
            sb.append("3. Try 'zhelp' for a list of Zishu commands.\n");
            sb.append("4. Use the Menu button for sessions, files, tools.\n");
        } else if (topic.equals("Terminal")) {
            sb.append("Terminal\n\n");
            sb.append("Real /system/bin/sh shell.\n");
            sb.append("ANSI colors supported.\n");
            sb.append("Use the keyboard bar for ESC, TAB, CTRL, arrows.\n");
            sb.append("zclear or the Clear button clears output.\n");
        } else if (topic.equals("Userspace")) {
            sb.append("Zishu Userspace\n\n");
            sb.append("PREFIX = files/usr\n");
            sb.append("HOME   = files/home\n");
            sb.append("TMPDIR = files/tmp\n");
            sb.append("BIN    = files/usr/bin\n");
            sb.append("\nRun 'zpath' to see actual paths.");
        } else if (topic.equals("Packages (zpkg)")) {
            sb.append("zpkg Package Manager\n\n");
            sb.append("zpkg help        Show help\n");
            sb.append("zpkg list        List installed\n");
            sb.append("zpkg install X   Install local package\n");
            sb.append("zpkg remove X    Remove\n");
            sb.append("zpkg repo ...    Manage repositories\n");
            sb.append("\nNo remote downloads happen unless\n");
            sb.append("you configure an HTTPS repository.\n");
        } else if (topic.equals("Projects")) {
            sb.append("Projects\n\n");
            sb.append("zproject create <name>\n");
            sb.append("zproject open <name>\n");
            sb.append("zproject build <name>\n");
            sb.append("zproject run <name>\n");
            sb.append("\nProjects live in $HOME/projects/<name>.\n");
        } else if (topic.equals("Editor")) {
            sb.append("Editor\n\n");
            sb.append("zedit <file>  Opens the editor.\n");
            sb.append("Supports common text formats.\n");
            sb.append("Save, Save As, Find, Undo.\n");
        } else if (topic.equals("Server")) {
            sb.append("Local Server\n\n");
            sb.append("zserver start [dir] [port]\n");
            sb.append("zserver stop\n");
            sb.append("zserver status\n");
            sb.append("\nBinds only to 127.0.0.1 by default.\n");
        } else if (topic.equals("Diagnostics")) {
            sb.append("Diagnostics\n\n");
            sb.append("zdoctor checks:\n");
            sb.append("  Shell, Userspace, Storage,\n");
            sb.append("  Package DB, Sessions, Tools.\n");
            sb.append("\nStatus is real — never faked.\n");
        } else if (topic.equals("Security")) {
            sb.append("Security Model\n\n");
            sb.append("- No root\n");
            sb.append("- No sandbox bypass\n");
            sb.append("- No telemetry\n");
            sb.append("- No automatic remote execution\n");
            sb.append("- Packages verified before install\n");
        } else if (topic.equals("Troubleshooting")) {
            sb.append("Troubleshooting\n\n");
            sb.append("Command not found?\n");
            sb.append("  The tool isn't installed.\n");
            sb.append("  Use 'ztools' to see what is available.\n\n");
            sb.append("Shell not responding?\n");
            sb.append("  Menu -> Restart Shell.\n\n");
            sb.append("Storage issues?\n");
            sb.append("  zstorage / zdoctor.\n");
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(topic)
                .setMessage(sb.toString())
                .setPositiveButton("Close", null)
                .show();
    }
}