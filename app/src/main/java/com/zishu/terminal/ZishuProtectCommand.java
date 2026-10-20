package com.zishu.terminal;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Command handler for zprotect — file encryption for Zishu Terminal.
 *
 * Usage:
 *   zprotect help
 *   zprotect encrypt <file> --password PASS [--delete]
 *   zprotect decrypt <file.zenc> --password PASS [--output FILE]
 *   zprotect info <file.zenc>
 *   zprotect verify <file.zenc> --password PASS
 *   zprotect list [dir]
 *   zprotect delete <file.zenc>
 */
public final class ZishuProtectCommand {

    public interface Output {
        void print(String s);
    }

    private final Context ctx;
    private final Output out;

    public ZishuProtectCommand(Context ctx, Output out) {
        this.ctx = ctx.getApplicationContext();
        this.out = out;
    }

    /** Returns true if the command was handled. */
    public boolean handle(String line, String cwd) {
        if (line == null) return false;
        String trimmed = line.trim();
        if (!trimmed.startsWith("zprotect")) return false;

        String[] parts = tokenize(trimmed);
        if (parts.length == 0) return false;
        if (!"zprotect".equals(parts[0])) return false;

        if (parts.length < 2) {
            printHelp();
            return true;
        }

        String sub = parts[1];
        if (sub.equals("help") || sub.equals("--help") || sub.equals("-h")) {
            printHelp();
            return true;
        }

        ZishuProtect.Logger log = new ZishuProtect.Logger() {
            @Override public void info(String s) { out.print(s); }
            @Override public void error(String s) { out.print("zprotect: " + s); }
        };

        if (sub.equals("encrypt")) {
            cmdEncrypt(parts, cwd, log);
        } else if (sub.equals("decrypt")) {
            cmdDecrypt(parts, cwd, log);
        } else if (sub.equals("info")) {
            cmdInfo(parts, cwd);
        } else if (sub.equals("verify")) {
            cmdVerify(parts, cwd);
        } else if (sub.equals("list")) {
            cmdList(parts, cwd);
        } else if (sub.equals("delete")) {
            cmdDelete(parts, cwd);
        } else {
            out.print("Unknown zprotect subcommand: " + sub);
            out.print("Try 'zprotect help'.");
        }
        return true;
    }

    // ---------------------------------------------------------------
    // Subcommands
    // ---------------------------------------------------------------

    private void cmdEncrypt(String[] parts, String cwd, ZishuProtect.Logger log) {
        if (parts.length < 3) {
            log.error("Usage: zprotect encrypt <file> --password PASS [--delete]");
            return;
        }
        File input = resolve(parts[2], cwd);
        if (input == null) { log.error("Invalid path."); return; }
        if (!input.isFile()) { log.error("Not a file: " + input.getAbsolutePath()); return; }

        // --password is required. We do NOT use an interactive prompt
        // because the terminal is line-based and cannot reliably block
        // for input. If --password is missing, we print an example.
        String password = extractPassword(parts);
        if (password == null) {
            log.error("No password provided.");
            log.error("Use: zprotect encrypt " + input.getName() + " --password YOURPASS");
            return;
        }
        if (password.length() == 0) {
            log.error("Password cannot be empty.");
            return;
        }

        File output = new File(input.getParentFile(), input.getName() + ZishuProtect.EXT);
        if (output.exists()) {
            log.error("Output already exists: " + output.getName());
            log.error("Delete it first, or use a different filename.");
            return;
        }

        log.info("Encrypting " + input.getName() + "...");
        boolean ok = ZishuProtect.encryptFile(input, output, password, log);
        if (!ok) return;

        if (hasFlag(parts, "--delete")) {
            if (input.delete()) log.info("Original deleted.");
            else log.info("Warning: could not delete original.");
        } else {
            log.info("Original kept. Use '--delete' flag to remove it.");
        }
    }

    private void cmdDecrypt(String[] parts, String cwd, ZishuProtect.Logger log) {
        if (parts.length < 3) {
            log.error("Usage: zprotect decrypt <file.zenc> --password PASS [--output FILE]");
            return;
        }
        File input = resolve(parts[2], cwd);
        if (input == null) { log.error("Invalid path."); return; }
        if (!input.isFile()) { log.error("Not a file: " + input.getAbsolutePath()); return; }
        if (!input.getName().endsWith(ZishuProtect.EXT)) {
            log.error("Not an encrypted file (missing " + ZishuProtect.EXT + " extension).");
            return;
        }

        String password = extractPassword(parts);
        if (password == null) {
            log.error("No password provided.");
            log.error("Use: zprotect decrypt " + input.getName() + " --password YOURPASS");
            return;
        }
        if (password.length() == 0) {
            log.error("Password cannot be empty.");
            return;
        }

        File output;
        String outArg = extractOption(parts, "--output");
        if (outArg != null) {
            output = resolve(outArg, cwd);
            if (output == null) { log.error("Invalid output path."); return; }
        } else {
            // Derive from filename by stripping .zenc
            String name = input.getName();
            if (name.endsWith(ZishuProtect.EXT)) {
                name = name.substring(0, name.length() - ZishuProtect.EXT.length());
            } else {
                name = name + ".decrypted";
            }
            output = new File(input.getParentFile(), name);
        }

        if (output.exists()) {
            log.error("Output already exists: " + output.getAbsolutePath());
            log.error("Delete it first, or use --output FILE.");
            return;
        }

        log.info("Decrypting " + input.getName() + "...");
        ZishuProtect.decryptFile(input, output, password, log);
    }

    private void cmdInfo(String[] parts, String cwd) {
        if (parts.length < 3) {
            out.print("Usage: zprotect info <file.zenc>");
            return;
        }
        File input = resolve(parts[2], cwd);
        if (input == null || !input.isFile()) {
            out.print("zprotect: file not found.");
            return;
        }
        ZishuProtect.FileInfo fi = ZishuProtect.readInfo(input);
        if (fi == null) {
            out.print("zprotect: not an encrypted file.");
            return;
        }
        out.print("Encrypted file: " + input.getName());
        out.print("  Format:          ZENC v" + fi.version);
        out.print("  Algorithm:       AES-256-GCM");
        out.print("  KDF iterations:  " + fi.iterations);
        out.print("  Original name:   " + fi.originalName);
        out.print("  Encrypted size:  " + fi.encryptedSize + " bytes");
        out.print("  Located at:      " + input.getAbsolutePath());
    }

    private void cmdVerify(String[] parts, String cwd) {
        if (parts.length < 3) {
            out.print("Usage: zprotect verify <file.zenc> --password PASS");
            return;
        }
        File input = resolve(parts[2], cwd);
        if (input == null || !input.isFile()) {
            out.print("zprotect: file not found.");
            return;
        }
        if (!input.getName().endsWith(ZishuProtect.EXT)) {
            out.print("zprotect: not an encrypted file.");
            return;
        }
        String password = extractPassword(parts);
        if (password == null) {
            out.print("zprotect: no password provided.");
            out.print("Use: zprotect verify " + input.getName() + " --password YOURPASS");
            return;
        }
        boolean ok = ZishuProtect.verifyPassword(input, password);
        if (ok) out.print("Password is correct.");
        else    out.print("Password is INCORRECT or file is corrupted.");
    }

    private void cmdList(String[] parts, String cwd) {
        File dir;
        if (parts.length >= 3) dir = resolve(parts[2], cwd);
        else dir = new File(cwd);
        if (dir == null || !dir.isDirectory()) {
            out.print("zprotect: invalid directory.");
            return;
        }
        File[] kids = dir.listFiles();
        if (kids == null) { out.print("zprotect: cannot read directory."); return; }
        int count = 0;
        for (int i = 0; i < kids.length; i++) {
            if (!kids[i].isFile()) continue;
            if (!kids[i].getName().endsWith(ZishuProtect.EXT)) continue;
            ZishuProtect.FileInfo fi = ZishuProtect.readInfo(kids[i]);
            if (fi == null) continue;
            out.print(kids[i].getName()
                    + "  (" + kids[i].length() + " bytes, was: "
                    + fi.originalName + ")");
            count++;
        }
        if (count == 0) {
            out.print("(no encrypted files in " + dir.getAbsolutePath() + ")");
        }
    }

    private void cmdDelete(String[] parts, String cwd) {
        if (parts.length < 3) {
            out.print("Usage: zprotect delete <file.zenc>");
            return;
        }
        File input = resolve(parts[2], cwd);
        if (input == null || !input.isFile()) {
            out.print("zprotect: file not found.");
            return;
        }
        if (!input.getName().endsWith(ZishuProtect.EXT)) {
            out.print("zprotect: refusing to delete non-encrypted file.");
            out.print("Only .zenc files can be deleted with zprotect delete.");
            return;
        }
        if (input.delete()) out.print("Deleted: " + input.getName());
        else out.print("zprotect: failed to delete.");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void printHelp() {
        out.print("Zishu Protect — file encryption");
        out.print("");
        out.print("Commands:");
        out.print("  zprotect encrypt <file> --password PASS");
        out.print("        Encrypt <file> to <file>.zenc");
        out.print("        Add --delete to remove the original file");
        out.print("");
        out.print("  zprotect decrypt <file.zenc> --password PASS");
        out.print("        Decrypt <file.zenc> back to its original name");
        out.print("        Add --output FILE to write to a different path");
        out.print("");
        out.print("  zprotect info <file.zenc>");
        out.print("        Show header info without decrypting");
        out.print("");
        out.print("  zprotect verify <file.zenc> --password PASS");
        out.print("        Test a password without writing any file");
        out.print("");
        out.print("  zprotect list [dir]");
        out.print("        List all .zenc files in the directory");
        out.print("");
        out.print("  zprotect delete <file.zenc>");
        out.print("        Delete an encrypted file");
        out.print("");
        out.print("  zprotect help");
        out.print("        Show this help");
        out.print("");
        out.print("Algorithm: AES-256-GCM with PBKDF2 key derivation");
        out.print("Format:    .zenc (portable, self-describing header)");
        out.print("");
        out.print("Examples:");
        out.print("  zprotect encrypt secret.txt --password mypass");
        out.print("  zprotect encrypt secret.txt --password mypass --delete");
        out.print("  zprotect decrypt secret.txt.zenc --password mypass");
        out.print("  zprotect info secret.txt.zenc");
        out.print("  zprotect verify secret.txt.zenc --password mypass");
        out.print("  zprotect list");
        out.print("  zprotect list $HOME");
        out.print("  zprotect delete secret.txt.zenc");
        out.print("");
        out.print("Warning: If you forget the password, the file cannot be");
        out.print("         recovered. There is no backdoor and no reset.");
    }

    private File resolve(String path, String cwd) {
        if (path == null) return null;
        path = path.trim();
        if (path.startsWith("\"") && path.endsWith("\"") && path.length() >= 2) {
            path = path.substring(1, path.length() - 1);
        }
        // Simple $HOME / $PREFIX / $TMPDIR expansion
        try {
            ZishuEnv env = new ZishuEnv(ctx);
            path = path.replace("$HOME", env.getHome().getAbsolutePath());
            path = path.replace("${HOME}", env.getHome().getAbsolutePath());
            path = path.replace("$PREFIX", env.getPrefix().getAbsolutePath());
            path = path.replace("${PREFIX}", env.getPrefix().getAbsolutePath());
            path = path.replace("$TMPDIR", env.getTmp().getAbsolutePath());
            path = path.replace("${TMPDIR}", env.getTmp().getAbsolutePath());
        } catch (Throwable ignored) {}

        File f = new File(path);
        if (f.isAbsolute()) return f;
        if (cwd == null) cwd = ".";
        return new File(cwd, path);
    }

    private String extractPassword(String[] parts) {
        return extractOption(parts, "--password");
    }

    private String extractOption(String[] parts, String flag) {
        for (int i = 0; i < parts.length - 1; i++) {
            if (flag.equals(parts[i])) return parts[i + 1];
        }
        return null;
    }

    private boolean hasFlag(String[] parts, String flag) {
        for (int i = 0; i < parts.length; i++) {
            if (flag.equals(parts[i])) return true;
        }
        return false;
    }

    private static String[] tokenize(String s) {
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && !inSingle) {
                cur.append(s.charAt(++i));
                any = true;
                continue;
            }
            if (c == '\'' && !inDouble) { inSingle = !inSingle; any = true; continue; }
            if (c == '"' && !inSingle) { inDouble = !inDouble; any = true; continue; }
            if ((c == ' ' || c == '\t') && !inSingle && !inDouble) {
                if (any) { out.add(cur.toString()); cur.setLength(0); any = false; }
                continue;
            }
            cur.append(c);
            any = true;
        }
        if (any) out.add(cur.toString());
        return out.toArray(new String[out.size()]);
    }
}