package com.zishu.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class TextEditorActivity extends Activity {

    private EditText editor;
    private TextView pathView;
    private TextView lineNumbers;
    private File currentFile;
    private boolean dirty;

    private String lastState = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_editor);

        editor = (EditText) findViewById(R.id.editorText);
        pathView = (TextView) findViewById(R.id.editorPath);
        lineNumbers = (TextView) findViewById(R.id.lineNumbers);

        Button btnSave = (Button) findViewById(R.id.btnSave);
        Button btnSaveAs = (Button) findViewById(R.id.btnSaveAs);
        Button btnFind = (Button) findViewById(R.id.btnSearch);
        Button btnUndo = (Button) findViewById(R.id.btnUndo);

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        btnSaveAs.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveAs(); }
        });
        btnFind.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { find(); }
        });
        btnUndo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { undo(); }
        });

        String path = getIntent().getStringExtra("path");
        if (path != null && path.length() > 0) {
            open(new File(path));
        } else {
            pathView.setText("(unsaved)");
            currentFile = new File(getFilesDir(), "untitled.txt");
            pathView.setText(currentFile.getAbsolutePath());
        }

        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                dirty = true;
                updateLineNumbers();
            }
        });

        updateLineNumbers();
    }

    private void open(File f) {
        currentFile = f;
        pathView.setText(f.getAbsolutePath());
        try {
            String text = FileUtils.readText(f);
            // Cap very large files
            if (text.length() > 800000) {
                text = text.substring(0, 800000);
                toast("File truncated for editing (large file)");
            }
            editor.setText(text);
            dirty = false;
        } catch (Throwable t) {
            toast("Cannot open: " + t.getMessage());
        }
    }

    private void updateLineNumbers() {
        int lines = editor.getLineCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            sb.append(i);
            if (i < lines) sb.append('\n');
        }
        lineNumbers.setText(sb.toString());
    }

    private void save() {
        if (currentFile == null) { saveAs(); return; }
        try {
            FileUtils.writeText(currentFile, editor.getText().toString());
            dirty = false;
            toast("Saved");
        } catch (Throwable t) {
            toast("Save failed: " + t.getMessage());
        }
    }

    private void saveAs() {
        final EditText input = new EditText(this);
        input.setText(currentFile != null ? currentFile.getAbsolutePath() : "");
        new AlertDialog.Builder(this)
                .setTitle("Save As")
                .setView(input)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String path = input.getText().toString().trim();
                        if (path.length() == 0) return;
                        File f = new File(path);
                        try {
                            FileUtils.writeText(f, editor.getText().toString());
                            currentFile = f;
                            pathView.setText(f.getAbsolutePath());
                            dirty = false;
                            toast("Saved");
                        } catch (Throwable t) {
                            toast("Save failed: " + t.getMessage());
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void find() {
        final EditText input = new EditText(this);
        input.setHint("Search text");
        new AlertDialog.Builder(this)
                .setTitle("Find")
                .setView(input)
                .setPositiveButton("Find", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String q = input.getText().toString();
                        if (q.length() == 0) return;
                        String text = editor.getText().toString();
                        int idx = text.indexOf(q, editor.getSelectionStart());
                        if (idx < 0) idx = text.indexOf(q);
                        if (idx < 0) { toast("Not found"); return; }
                        editor.setSelection(idx, idx + q.length());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void undo() {
        // Simple undo using Android's built-in EditText undo via key event is unreliable;
        // Provide a minimal "revert to last save" instead.
        if (currentFile == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Undo")
                .setMessage("Revert to last saved version?")
                .setPositiveButton("Revert", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        try {
                            editor.setText(FileUtils.readText(currentFile));
                            dirty = false;
                        } catch (Throwable t) {
                            toast("Undo failed: " + t.getMessage());
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}