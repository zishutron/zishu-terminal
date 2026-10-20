package com.zishu.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class FileManagerActivity extends Activity {

    private File currentDir;
    private ArrayList<File> items = new ArrayList<File>();
    private FileListAdapter adapter;
    private TextView pathText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_manager);

        pathText = (TextView) findViewById(R.id.pathText);
        ListView list = (ListView) findViewById(R.id.fileList);

        currentDir = getFilesDir();

        adapter = new FileListAdapter();
        list.setAdapter(adapter);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File f = items.get(position);
                if (f.isDirectory()) {
                    currentDir = f;
                    refresh();
                } else {
                    Intent i = new Intent(FileManagerActivity.this, TextEditorActivity.class);
                    i.putExtra("path", f.getAbsolutePath());
                    startActivity(i);
                }
            }
        });

        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showActions(items.get(position));
                return true;
            }
        });

        Button btnUp = (Button) findViewById(R.id.btnUp);
        Button btnRefresh = (Button) findViewById(R.id.btnRefresh);
        Button btnNewFolder = (Button) findViewById(R.id.btnNewFolder);
        Button btnNewFile = (Button) findViewById(R.id.btnNewFile);

        btnUp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                File parent = currentDir.getParentFile();
                if (parent != null) {
                    currentDir = parent;
                    refresh();
                }
            }
        });

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refresh(); }
        });

        btnNewFolder.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { promptNew(true); }
        });

        btnNewFile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { promptNew(false); }
        });

        refresh();
    }

    private void refresh() {
        pathText.setText(currentDir.getAbsolutePath());
        items.clear();
        File[] kids = currentDir.listFiles();
        if (kids != null) {
            Arrays.sort(kids, new Comparator<File>() {
                @Override public int compare(File a, File b) {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
            for (int i = 0; i < kids.length; i++) items.add(kids[i]);
        }
        adapter.notifyDataSetChanged();
    }

    private void promptNew(final boolean folder) {
        final EditText input = new EditText(this);
        input.setHint(folder ? "Folder name" : "File name");
        new AlertDialog.Builder(this)
                .setTitle(folder ? "New Folder" : "New File")
                .setView(input)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String name = input.getText().toString().trim();
                        if (name.length() == 0) return;
                        File f = new File(currentDir, name);
                        try {
                            boolean ok;
                            if (folder) ok = f.mkdirs();
                            else {
                                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                                ok = f.createNewFile();
                            }
                            if (!ok) toast("Failed to create " + name);
                        } catch (Throwable t) {
                            toast("Error: " + t.getMessage());
                        }
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showActions(final File f) {
        final String[] options = {"Open", "Rename", "Delete", "Copy to…", "Move to…", "Info"};
        new AlertDialog.Builder(this)
                .setTitle(f.getName())
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            if (f.isDirectory()) { currentDir = f; refresh(); }
                            else {
                                Intent i = new Intent(FileManagerActivity.this, TextEditorActivity.class);
                                i.putExtra("path", f.getAbsolutePath());
                                startActivity(i);
                            }
                        } else if (which == 1) rename(f);
                        else if (which == 2) deleteConfirm(f);
                        else if (which == 3) copyMove(f, false);
                        else if (which == 4) copyMove(f, true);
                        else showInfo(f);
                    }
                })
                .show();
    }

    private void rename(final File f) {
        final EditText input = new EditText(this);
        input.setText(f.getName());
        new AlertDialog.Builder(this)
                .setTitle("Rename")
                .setView(input)
                .setPositiveButton("Rename", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String name = input.getText().toString().trim();
                        if (name.length() == 0) return;
                        File dest = new File(f.getParentFile(), name);
                        if (!f.renameTo(dest)) toast("Rename failed");
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteConfirm(final File f) {
        new AlertDialog.Builder(this)
                .setTitle("Delete")
                .setMessage("Delete " + f.getName() + "?")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (!FileUtils.deleteRecursive(f)) toast("Delete failed");
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void copyMove(final File src, final boolean move) {
        final EditText input = new EditText(this);
        input.setHint("Target directory absolute path");
        input.setText(currentDir.getAbsolutePath());
        new AlertDialog.Builder(this)
                .setTitle(move ? "Move to" : "Copy to")
                .setView(input)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String dest = input.getText().toString().trim();
                        if (dest.length() == 0) return;
                        File destDir = new File(dest);
                        if (!destDir.isDirectory()) { toast("Not a directory"); return; }
                        File target = new File(destDir, src.getName());
                        boolean ok = FileUtils.copy(src, target);
                        if (ok && move) FileUtils.deleteRecursive(src);
                        if (!ok) toast("Operation failed");
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showInfo(File f) {
        StringBuilder sb = new StringBuilder();
        sb.append("Path: ").append(f.getAbsolutePath()).append("\n");
        sb.append("Type: ").append(f.isDirectory() ? "Directory" : "File").append("\n");
        sb.append("Size: ").append(f.isDirectory() ? "-" : FileUtils.humanSize(f.length())).append("\n");
        sb.append("Modified: ").append(FileUtils.formatDate(f.lastModified())).append("\n");
        sb.append("Readable: ").append(f.canRead()).append("\n");
        sb.append("Writable: ").append(f.canWrite());
        new AlertDialog.Builder(this)
                .setTitle("Info")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private class FileListAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(FileManagerActivity.this)
                        .inflate(R.layout.row_file, parent, false);
            }
            File f = items.get(position);
            ImageView icon = (ImageView) v.findViewById(R.id.rowIcon);
            TextView name = (TextView) v.findViewById(R.id.rowName);
            TextView meta = (TextView) v.findViewById(R.id.rowMeta);

            icon.setImageResource(f.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file);
            name.setText(f.isDirectory() ? f.getName() + "/" : f.getName());
            if (f.isDirectory()) {
                meta.setText("Directory");
            } else {
                meta.setText(FileUtils.humanSize(f.length()) + "  ·  " + FileUtils.formatDate(f.lastModified()));
            }
            return v;
        }
    }
}