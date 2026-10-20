package com.zishu.terminal;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class DiagnosticsActivity extends Activity {

    private TextView txt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);
        txt = (TextView) findViewById(R.id.diagText);
        Button rerun = (Button) findViewById(R.id.btnRerun);
        rerun.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { run(); }
        });
        run();
    }

    private void run() {
        final StringBuilder sb = new StringBuilder();
        new ZishuDoctor(this, new ZishuDoctor.Logger() {
            @Override public void line(String s) { sb.append(s).append('\n'); }
        }).run();
        txt.setText(sb.toString());
    }
}