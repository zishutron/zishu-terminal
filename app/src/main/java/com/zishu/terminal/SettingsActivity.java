package com.zishu.terminal;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    // ---- Terminal settings ----
    private SeekBar seekFont, seekPad;
    private TextView labelFont, labelPad;
    private RadioButton themeDark, themeLight, themeGreen;
    private Spinner spinnerCursor;
    private CheckBox chkBlink, chkTimestamps, chkKeepAwake, chkConfirm;
    private EditText editHistory;

    // ---- Startup & Home settings ----
    private CheckBox chkBannerEnabled, chkShowVersion, chkShowDate, chkShowDevice;
    private Spinner spinnerBannerStyle, spinnerBannerColor;
    private EditText editCustomBanner, editWelcomeText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // ---------------- Terminal settings ----------------
        seekFont = (SeekBar) findViewById(R.id.seekFontSize);
        seekPad = (SeekBar) findViewById(R.id.seekPadding);
        labelFont = (TextView) findViewById(R.id.labelFontSize);
        labelPad = (TextView) findViewById(R.id.labelPadding);
        themeDark = (RadioButton) findViewById(R.id.themeDark);
        themeLight = (RadioButton) findViewById(R.id.themeLight);
        themeGreen = (RadioButton) findViewById(R.id.themeGreen);
        spinnerCursor = (Spinner) findViewById(R.id.spinnerCursor);
        chkBlink = (CheckBox) findViewById(R.id.chkCursorBlink);
        chkTimestamps = (CheckBox) findViewById(R.id.chkTimestamps);
        chkKeepAwake = (CheckBox) findViewById(R.id.chkKeepAwake);
        chkConfirm = (CheckBox) findViewById(R.id.chkConfirmClear);
        editHistory = (EditText) findViewById(R.id.editHistorySize);

        // ---------------- Startup settings ----------------
        chkBannerEnabled = (CheckBox) findViewById(R.id.chkBannerEnabled);
        spinnerBannerStyle = (Spinner) findViewById(R.id.spinnerBannerStyle);
        spinnerBannerColor = (Spinner) findViewById(R.id.spinnerBannerColor);
        editCustomBanner = (EditText) findViewById(R.id.editCustomBanner);
        editWelcomeText = (EditText) findViewById(R.id.editWelcomeText);
        chkShowVersion = (CheckBox) findViewById(R.id.chkShowVersion);
        chkShowDate = (CheckBox) findViewById(R.id.chkShowDate);
        chkShowDevice = (CheckBox) findViewById(R.id.chkShowDevice);

        // ---------------- Cursor spinner ----------------
        ArrayAdapter<String> cursorAdapter = new ArrayAdapter<String>(this,
                R.layout.spinner_item,
                new String[]{"Block", "Underline", "Bar"});
        cursorAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerCursor.setAdapter(cursorAdapter);

        // ---------------- Banner style spinner ----------------
        ArrayAdapter<String> bannerStyleAdapter = new ArrayAdapter<String>(this,
                R.layout.spinner_item,
                new String[]{
                        "Block (default)",
                        "ASCII Art",
                        "Box",
                        "Minimal",
                        "Hacker",
                        "Custom",
                        "None"
                });
        bannerStyleAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerBannerStyle.setAdapter(bannerStyleAdapter);

        // ---------------- Banner color spinner ----------------
        ArrayAdapter<String> bannerColorAdapter = new ArrayAdapter<String>(this,
                R.layout.spinner_item,
                new String[]{
                        "Accent",
                        "White",
                        "Green",
                        "Cyan",
                        "Yellow",
                        "Red"
                });
        bannerColorAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerBannerColor.setAdapter(bannerColorAdapter);

        // ---------------- Custom banner: monospace, multiline ----------------
        editCustomBanner.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editCustomBanner.setHorizontallyScrolling(false);
        editCustomBanner.setMaxLines(20);

        // ================================================================
        // Load current settings
        // ================================================================

        // ---- Terminal ----
        int fontSize = Prefs.getInt(this, Prefs.KEY_FONT_SIZE, Prefs.DEFAULT_FONT_SIZE);
        int padding  = Prefs.getInt(this, Prefs.KEY_PADDING, Prefs.DEFAULT_PADDING);
        int theme    = Prefs.getInt(this, Prefs.KEY_THEME, Prefs.DEFAULT_THEME);

        seekFont.setMax(30);
        seekFont.setProgress(Math.max(0, fontSize - 8));
        seekPad.setMax(32);
        seekPad.setProgress(padding);
        updateFontLabel();
        updatePadLabel();

        if (theme == Prefs.THEME_LIGHT) themeLight.setChecked(true);
        else if (theme == Prefs.THEME_GREEN) themeGreen.setChecked(true);
        else themeDark.setChecked(true);

        int cursor = Prefs.getInt(this, Prefs.KEY_CURSOR_STYLE, 0);
        if (cursor >= 0 && cursor < 3) spinnerCursor.setSelection(cursor);

        chkBlink.setChecked(Prefs.getBool(this, Prefs.KEY_CURSOR_BLINK, true));
        chkTimestamps.setChecked(Prefs.getBool(this, Prefs.KEY_TIMESTAMPS, false));
        chkKeepAwake.setChecked(Prefs.getBool(this, Prefs.KEY_KEEP_AWAKE, false));
        chkConfirm.setChecked(Prefs.getBool(this, Prefs.KEY_CONFIRM_CLEAR, false));
        editHistory.setText(String.valueOf(
                Prefs.getInt(this, Prefs.KEY_HISTORY_SIZE, Prefs.DEFAULT_HISTORY_SIZE)));

        // ---- Startup ----
        chkBannerEnabled.setChecked(StartupPrefs.isBannerEnabled(this));

        int bannerStyle = StartupPrefs.getBannerStyle(this);
        if (bannerStyle < 0 || bannerStyle > 6) bannerStyle = StartupPrefs.DEFAULT_BANNER_STYLE;
        spinnerBannerStyle.setSelection(bannerStyle);

        int bannerColor = StartupPrefs.getBannerColor(this);
        if (bannerColor < 0 || bannerColor > 5) bannerColor = StartupPrefs.DEFAULT_BANNER_COLOR;
        spinnerBannerColor.setSelection(bannerColor);

        editCustomBanner.setText(StartupPrefs.getCustomBanner(this));
        editWelcomeText.setText(StartupPrefs.getWelcomeText(this));

        chkShowVersion.setChecked(StartupPrefs.showVersion(this));
        chkShowDate.setChecked(StartupPrefs.showDate(this));
        chkShowDevice.setChecked(StartupPrefs.showDevice(this));

        // ================================================================
        // Listeners
        // ================================================================

        seekFont.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) { updateFontLabel(); }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        seekPad.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) { updatePadLabel(); }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        Button save = (Button) findViewById(R.id.btnSaveSettings);
        Button reset = (Button) findViewById(R.id.btnResetSettings);

        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveSettings(); }
        });
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { resetSettings(); }
        });
    }

    // ================================================================
    // Labels
    // ================================================================

    private void updateFontLabel() {
        labelFont.setText("Font size: " + (seekFont.getProgress() + 8) + " sp");
    }

    private void updatePadLabel() {
        labelPad.setText("Padding: " + seekPad.getProgress() + " dp");
    }

    // ================================================================
    // Save
    // ================================================================

    private void saveSettings() {
        // ---- Terminal ----
        Prefs.putInt(this, Prefs.KEY_FONT_SIZE, seekFont.getProgress() + 8);
        Prefs.putInt(this, Prefs.KEY_PADDING, seekPad.getProgress());

        int theme = Prefs.THEME_DARK;
        if (themeLight.isChecked()) theme = Prefs.THEME_LIGHT;
        else if (themeGreen.isChecked()) theme = Prefs.THEME_GREEN;
        Prefs.putInt(this, Prefs.KEY_THEME, theme);

        Prefs.putInt(this, Prefs.KEY_CURSOR_STYLE, spinnerCursor.getSelectedItemPosition());
        Prefs.putBool(this, Prefs.KEY_CURSOR_BLINK, chkBlink.isChecked());
        Prefs.putBool(this, Prefs.KEY_TIMESTAMPS, chkTimestamps.isChecked());
        Prefs.putBool(this, Prefs.KEY_KEEP_AWAKE, chkKeepAwake.isChecked());
        Prefs.putBool(this, Prefs.KEY_CONFIRM_CLEAR, chkConfirm.isChecked());

        int size;
        try {
            size = Integer.parseInt(editHistory.getText().toString().trim());
        } catch (Throwable t) {
            size = Prefs.DEFAULT_HISTORY_SIZE;
        }
        Prefs.putInt(this, Prefs.KEY_HISTORY_SIZE, size);
        new CommandHistory(this).setMax(size);

        // ---- Startup ----
        StartupPrefs.setBannerEnabled(this, chkBannerEnabled.isChecked());
        StartupPrefs.setBannerStyle(this, spinnerBannerStyle.getSelectedItemPosition());
        StartupPrefs.setBannerColor(this, spinnerBannerColor.getSelectedItemPosition());
        StartupPrefs.setCustomBanner(this, editCustomBanner.getText().toString());

        String welcome = editWelcomeText.getText().toString();
        if (welcome == null || welcome.trim().length() == 0) {
            welcome = StartupPrefs.DEFAULT_WELCOME_TEXT;
        }
        StartupPrefs.setWelcomeText(this, welcome);

        StartupPrefs.setShowVersion(this, chkShowVersion.isChecked());
        StartupPrefs.setShowDate(this, chkShowDate.isChecked());
        StartupPrefs.setShowDevice(this, chkShowDevice.isChecked());

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
    }

    // ================================================================
    // Reset
    // ================================================================

    private void resetSettings() {
        Prefs.reset(this);
        Toast.makeText(this, "Settings reset", Toast.LENGTH_SHORT).show();
        finish();
    }
}
