package com.zishu.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;

public class ProjectsActivity extends Activity {

    private ProjectManager pm;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_projects);

        pm = new ProjectManager(this, new ProjectManager.Logger() {
            @Override public void line(String s) {
                Toast.makeText(ProjectsActivity.this, s, Toast.LENGTH_SHORT).show();
            }
        });

        ListView list = (ListView) findViewById(R.id.projList);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        list.setAdapter(adapter);
        reload();

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                String name = adapter.getItem(pos);
                if (name == null || name.startsWith("(")) return;
                File dir = pm.open(name);
                Toast.makeText(ProjectsActivity.this,
                        dir.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            }
        });

        Button nw = (Button) findViewById(R.id.btnProjNew);
        Button rf = (Button) findViewById(R.id.btnProjRefresh);
        nw.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { promptNew(); }
        });
        rf.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { reload(); }
        });
    }

    private void reload() {
        adapter.clear();
        File root = pm.root();
        File[] kids = root.listFiles();
        if (kids == null || kids.length == 0) adapter.add("(no projects)");
        else for (int i = 0; i < kids.length; i++)
            if (kids[i].isDirectory()) adapter.add(kids[i].getName());
        adapter.notifyDataSetChanged();
    }

    private void promptNew() {
        final EditText in = new EditText(this);
        in.setHint("project name");
        new AlertDialog.Builder(this)
                .setTitle("New Project")
                .setView(in)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        pm.create(in.getText().toString().trim());
                        reload();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}