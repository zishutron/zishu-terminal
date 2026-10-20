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
import java.util.List;

public class SessionsActivity extends Activity {

    private SessionManager mgr;
    private ArrayAdapter<String> adapter;
    private ListView list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sessions);

        mgr = SessionManager.get();

        list = (ListView) findViewById(R.id.sessionList);
        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1);
        list.setAdapter(adapter);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                List<SessionManager.Session> all = mgr.all();
                if (position < 0 || position >= all.size()) return;
                mgr.setActive(all.get(position).id);
                Toast.makeText(SessionsActivity.this,
                        "Active: " + all.get(position).name,
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                List<SessionManager.Session> all = mgr.all();
                if (position < 0 || position >= all.size()) return false;
                showActions(all.get(position));
                return true;
            }
        });

        Button newBtn = (Button) findViewById(R.id.btnNewSession);
        newBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String cwd = getFilesDir().getAbsolutePath();
                new File(cwd).mkdirs();

                // If there are no sessions yet, create first one.
                int n = mgr.all().size() + 1;
                SessionManager.Session s = mgr.create("Terminal " + n, cwd, null);

                // Attach a real shell with a listener that routes output to the
                // shared MainActivity buffer via the session buffer.
                final SessionManager.Session created = s;
                s.shell = new ShellProcess(cwd, new ShellProcess.Listener() {
                    @Override public void onOutput(String line) {
                        created.buffer.append(line).append('\n');
                    }
                    @Override public void onError(String line) {
                        created.buffer.append(line).append('\n');
                    }
                    @Override public void onExit(int code) {
                        created.buffer.append("[session exited ").append(code).append("]\n");
                    }
                });
                s.shell.start();

                mgr.setActive(s.id);
                reload();
            }
        });

        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh every time screen appears so newly-created sessions show up.
        reload();
    }

    private void showActions(final SessionManager.Session s) {
        final String[] options = {"Set Active", "Rename", "Restart", "Close"};
        new AlertDialog.Builder(this)
                .setTitle(s.name)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            mgr.setActive(s.id);
                            Toast.makeText(SessionsActivity.this,
                                    "Active: " + s.name, Toast.LENGTH_SHORT).show();
                            finish();
                        } else if (which == 1) {
                            rename(s);
                        } else if (which == 2) {
                            if (s.shell != null) s.shell.stop();
                            s.shell = new ShellProcess(s.cwd, new ShellProcess.Listener() {
                                @Override public void onOutput(String line) {
                                    s.buffer.append(line).append('\n');
                                }
                                @Override public void onError(String line) {
                                    s.buffer.append(line).append('\n');
                                }
                                @Override public void onExit(int code) {}
                            });
                            s.shell.start();
                            Toast.makeText(SessionsActivity.this,
                                    "Restarted " + s.name, Toast.LENGTH_SHORT).show();
                            reload();
                        } else if (which == 3) {
                            if (mgr.all().size() <= 1) {
                                Toast.makeText(SessionsActivity.this,
                                        "Cannot close the last session",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            mgr.remove(s.id);
                            reload();
                        }
                    }
                })
                .show();
    }

    private void rename(final SessionManager.Session s) {
        final EditText input = new EditText(this);
        input.setText(s.name);
        new AlertDialog.Builder(this)
                .setTitle("Rename session")
                .setView(input)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String n = input.getText().toString().trim();
                        if (n.length() > 0) {
                            s.name = n;
                            reload();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void reload() {
        adapter.clear();
        List<SessionManager.Session> all = mgr.all();
        int activeId = mgr.getActiveId();
        for (int i = 0; i < all.size(); i++) {
            SessionManager.Session s = all.get(i);
            String prefix = (s.id == activeId) ? "▶  " : "   ";
            String running = (s.shell != null && s.shell.isRunning()) ? "  [running]" : "  [stopped]";
            adapter.add(prefix + s.name + running);
        }
        adapter.notifyDataSetChanged();
    }
}