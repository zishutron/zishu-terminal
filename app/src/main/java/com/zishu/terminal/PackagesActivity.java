package com.zishu.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

public class PackagesActivity extends Activity {

    private PackageDatabase db;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_packages);

        db = new PackageDatabase(this);
        ListView list = (ListView) findViewById(R.id.pkgList);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        list.setAdapter(adapter);
        reload();

        Button refresh = (Button) findViewById(R.id.btnPkgRefresh);
        Button imp = (Button) findViewById(R.id.btnPkgImport);

        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { reload(); }
        });
        imp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { promptInstall(); }
        });
    }

    private void reload() {
        adapter.clear();
        List<PackageInfo> all = db.all();
        if (all.isEmpty()) {
            adapter.add("(no packages installed)");
        } else {
            for (int i = 0; i < all.size(); i++) {
                PackageInfo p = all.get(i);
                adapter.add(p.name + " " + p.version + "  [" + p.architecture + "]");
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void promptInstall() {
        final EditText input = new EditText(this);
        input.setHint("/path/to/package.zpkg");
        new AlertDialog.Builder(this)
                .setTitle("Install local package")
                .setView(input)
                .setPositiveButton("Install", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        final String path = input.getText().toString().trim();
                        if (path.length() == 0) return;
                        ZpkgManager mgr = new ZpkgManager(PackagesActivity.this,
                                new ZpkgManager.Logger() {
                                    @Override public void line(String s) {
                                        Toast.makeText(PackagesActivity.this, s,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                        mgr.install(path);
                        reload();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}