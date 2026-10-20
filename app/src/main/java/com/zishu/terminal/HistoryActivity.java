package com.zishu.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import java.util.List;

public class HistoryActivity extends Activity {

    private CommandHistory history;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        history = new CommandHistory(this);

        ListView list = (ListView) findViewById(R.id.historyList);
        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1);
        list.setAdapter(adapter);
        reload();

        Button clear = (Button) findViewById(R.id.btnClearHistory);
        Button close = (Button) findViewById(R.id.btnCloseHistory);

        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(HistoryActivity.this)
                        .setTitle("Clear history")
                        .setMessage("Delete all stored commands?")
                        .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                history.clear();
                                reload();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    private void reload() {
        adapter.clear();
        List<String> all = history.all();
        for (int i = 0; i < all.size(); i++) {
            adapter.add((i + 1) + "  " + all.get(i));
        }
        adapter.notifyDataSetChanged();
    }
}