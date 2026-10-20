package com.zishu.terminal;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

public class SystemInfoActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_system_info);

        TextView tv = (TextView) findViewById(R.id.infoText);

        StringBuilder sb = new StringBuilder();
        sb.append("Zishu Terminal — System Information\n\n");

        sb.append("Application Version: 6.0.0\n");
        sb.append("Release Channel:     Stable\n\n");

        sb.append("Device Manufacturer: ").append(safe(Build.MANUFACTURER)).append('\n');
        sb.append("Model:               ").append(safe(Build.MODEL)).append('\n');
        sb.append("Android Version:     ").append(safe(Build.VERSION.RELEASE)).append('\n');
        sb.append("SDK:                 ").append(Build.VERSION.SDK_INT).append('\n');

        String abi = (Build.SUPPORTED_ABIS.length > 0) ? Build.SUPPORTED_ABIS[0] : "unknown";
        sb.append("Architecture ABI:    ").append(abi).append('\n');

        StringBuilder abis = new StringBuilder();
        String[] a = Build.SUPPORTED_ABIS;
        for (int i = 0; i < a.length; i++) {
            if (i > 0) abis.append(", ");
            abis.append(a[i]);
        }
        sb.append("All ABIs:            ").append(abis.toString()).append('\n');

        Runtime rt = Runtime.getRuntime();
        sb.append("Runtime Free Memory: ").append(formatBytes(rt.freeMemory())).append('\n');
        sb.append("Runtime Max Memory:  ").append(formatBytes(rt.maxMemory())).append('\n');
        sb.append("App Data Directory:  ").append(getFilesDir().getAbsolutePath()).append('\n');

        sb.append("Root access:         ").append(hasRoot() ? "Available" : "Not available").append('\n');

        try {
            StatFs fs = new StatFs(getFilesDir().getAbsolutePath());
            long bs = fs.getBlockSizeLong();
            sb.append("Data Free:           ").append(formatBytes(fs.getAvailableBlocksLong() * bs)).append('\n');
            sb.append("Data Total:          ").append(formatBytes(fs.getBlockCountLong() * bs)).append('\n');
        } catch (Throwable t) {
            sb.append("Data Free:           unavailable\n");
        }

        try {
            File ext = Environment.getExternalStorageDirectory();
            if (ext != null) {
                sb.append("External Storage:    ").append(ext.getAbsolutePath()).append('\n');
            }
        } catch (Throwable ignored) {}

        sb.append("\n─── Current limitations ───\n");
        sb.append("Developer toolchains are not bundled.\n");
        sb.append("Remote package fetching is not enabled.\n");

        tv.setText(sb.toString());
    }

    private static boolean hasRoot() {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su"};
        for (int i = 0; i < paths.length; i++) {
            try {
                if (new File(paths[i]).exists()) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static String safe(String s) {
        return s == null ? "unknown" : s;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.US, "%.2f GB", gb);
    }
}
