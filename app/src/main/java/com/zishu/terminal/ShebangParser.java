package com.zishu.terminal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Parses the shebang line of a script file.
 *
 * Examples:
 *   "#!/bin/bash"              → "bash"
 *   "#!/usr/bin/env bash"      → "bash"
 *   "#!/system/bin/sh"         → "sh"
 *   "#!/usr/bin/python3"       → "python3"
 *   "#!/usr/bin/env python3"   → "python3"
 *   (no shebang)               → null
 *
 * The returned string is just the interpreter name (e.g. "bash").
 * Path resolution is handled by InterpreterResolver.
 *
 * Traditional Java 6/7 syntax. No lambdas. No AndroidX.
 */
public final class ShebangParser {

    private static final int MAX_FIRST_LINE = 1024;

    /**
     * Returns the interpreter name (e.g. "bash", "python3"), or null
     * if the file has no shebang or cannot be read.
     */
    public static String parse(File script) {
        if (script == null || !script.isFile()) return null;

        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(script), "UTF-8"));

            String firstLine = r.readLine();
            if (firstLine == null) return null;

            // Trim BOM if present
            if (firstLine.length() > 0 && firstLine.charAt(0) == '\uFEFF') {
                firstLine = firstLine.substring(1);
            }

            if (firstLine.length() < 3) return null;
            if (firstLine.charAt(0) != '#' || firstLine.charAt(1) != '!') {
                return null;
            }

            // Remove "#!"
            String rest = firstLine.substring(2).trim();
            if (rest.length() == 0) return null;

            // Handle "#! /bin/bash" (space after #!)
            if (rest.length() > MAX_FIRST_LINE) {
                rest = rest.substring(0, MAX_FIRST_LINE);
            }

            // Split into tokens
            String[] parts = rest.split("\\s+");
            if (parts.length == 0) return null;

            String first = parts[0];
            String name;

            // Case 1: "#!/usr/bin/env bash" or "#!/usr/bin/env python3"
            if (first.endsWith("/env") || first.equals("env")) {
                if (parts.length < 2) return null;
                name = parts[1];
                // Handle "env -S name arg" (rare)
                if (name.equals("-S") || name.equals("-i") || name.startsWith("-")) {
                    if (parts.length < 3) return null;
                    name = parts[2];
                }
            } else {
                // Case 2: direct path — take basename
                int slash = first.lastIndexOf('/');
                if (slash >= 0) {
                    name = first.substring(slash + 1);
                } else {
                    name = first;
                }
            }

            if (name.length() == 0) return null;
            return name;

        } catch (IOException e) {
            return null;
        } catch (Throwable t) {
            return null;
        } finally {
            if (r != null) {
                try { r.close(); } catch (IOException ignored) {}
            }
        }
    }

    private ShebangParser() {}
}