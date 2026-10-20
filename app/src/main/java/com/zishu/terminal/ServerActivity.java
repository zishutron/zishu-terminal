package com.zishu.terminal;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * UI for controlling zserver. It talks to the same ZishuServer instance
 * used by the terminal commands.
 */
public class ServerActivity extends Activity {

    private static ZishuServer sharedServer;
    private TextView status;
    private EditText rootInput, portInput;

    public static ZishuServer getSharedServer() {
        if (sharedServer == null) {
            sharedServer = new ZishuServer(new ZishuServer.Logger() {
                @Override public void line(String s) { /* silent by default */ }
            });
        }
        return sharedServer;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(0xFF0B0F0C);

        status = new TextView(this);
        status.setTextColor(0xFF7CFF7C);
        status.setTextSize(14);

        rootInput = new EditText(this);
        rootInput.setHint("Root directory (absolute path)");
        rootInput.setTextColor(0xFFE8E8E8);
        rootInput.setHintTextColor(0xFF666666);

        portInput = new EditText(this);
        portInput.setHint("Port");
        portInput.setText("8080");
        portInput.setTextColor(0xFFE8E8E8);
        portInput.setHintTextColor(0xFF666666);

        Button start = new Button(this);
        start.setText("Start");
        Button stop = new Button(this);
        stop.setText("Stop");
        Button refresh = new Button(this);
        refresh.setText("Refresh Status");

        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doStart(); }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doStop(); }
        });
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refresh(); }
        });

        root.addView(status);
        root.addView(rootInput);
        root.addView(portInput);
        root.addView(start);
        root.addView(stop);
        root.addView(refresh);
        setContentView(root);

        // Default: current cwd hint (files/home)
        rootInput.setText(new ZishuEnv(this).getHome().getAbsolutePath());
        refresh();
    }

    private void doStart() {
        String path = rootInput.getText().toString().trim();
        int port = 8080;
        try { port = Integer.parseInt(portInput.getText().toString().trim()); }
        catch (Throwable ignored) {}
        File root = new File(path);
        if (!root.isDirectory()) {
            Toast.makeText(this, "Not a directory: " + path, Toast.LENGTH_SHORT).show();
            return;
        }
        getSharedServer().start(root, port);
        refresh();
    }

    private void doStop() {
        getSharedServer().stop();
        refresh();
    }

    private void refresh() {
        boolean running = getSharedServer().isRunning();
        status.setText("Server: " + (running ? "RUNNING" : "STOPPED"));
    }
}