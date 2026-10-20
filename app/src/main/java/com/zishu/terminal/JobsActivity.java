package com.zishu.terminal;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.util.List;

public class JobsActivity extends Activity {
    private ArrayAdapter<String> adapter;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0F0C);

        ListView lv = new ListView(this);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        lv.setAdapter(adapter);

        Button refresh = new Button(this);
        refresh.setText("Refresh");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { reload(); }
        });

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                List<JobManager.Job> all = JobManager.get().all();
                if (pos >= 0 && pos < all.size()) {
                    JobManager.get().stop(all.get(pos).id);
                    reload();
                }
            }
        });

        root.addView(lv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(refresh);
        setContentView(root);
        reload();
    }

    private void reload() {
        h.post(new Runnable() {
            @Override public void run() {
                adapter.clear();
                List<JobManager.Job> all = JobManager.get().all();
                if (all.isEmpty()) adapter.add("(no jobs)");
                else for (int i = 0; i < all.size(); i++) {
                    JobManager.Job j = all.get(i);
                    adapter.add("#" + j.id + " " + j.name + "  [" + j.status + "]  " + j.command);
                }
                adapter.notifyDataSetChanged();
            }
        });
    }
}