package com.zishu.terminal;

import java.io.File;

/**
 * Detects the project type and returns a real build command. Never
 * fabricates results.
 */
public class BuildSystem {

    public static class Plan {
        public String type;      // "shell", "make", "gradle", "npm", "python", "unknown"
        public String command;   // command to run, or null if unsupported
        public String message;
    }

    public static Plan detect(File projectDir, ZishuEnv env) {
        Plan p = new Plan();
        if (projectDir == null || !projectDir.isDirectory()) {
            p.type = "unknown"; p.message = "Invalid project directory"; return p;
        }
        if (new File(projectDir, "Makefile").isFile()) {
            if (ToolRegistry.findInPath(env, "make") != null) {
                p.type = "make"; p.command = "make"; p.message = "make detected";
            } else {
                p.type = "make"; p.command = null;
                p.message = "Makefile found, but 'make' is not installed.";
            }
            return p;
        }
        if (new File(projectDir, "package.json").isFile()) {
            if (ToolRegistry.findInPath(env, "npm") != null) {
                p.type = "npm"; p.command = "npm run build"; p.message = "package.json detected";
            } else {
                p.type = "npm"; p.command = null;
                p.message = "package.json found, but 'npm' is not installed.";
            }
            return p;
        }
        if (new File(projectDir, "setup.py").isFile()) {
            if (ToolRegistry.findInPath(env, "python") != null ||
                    ToolRegistry.findInPath(env, "python3") != null) {
                p.type = "python"; p.command = "python setup.py build"; p.message = "setup.py detected";
            } else {
                p.type = "python"; p.command = null;
                p.message = "setup.py found, but Python is not installed.";
            }
            return p;
        }
        // Look for a build.sh
        File buildSh = new File(projectDir, "build.sh");
        if (buildSh.isFile()) {
            p.type = "shell"; p.command = "sh build.sh"; p.message = "build.sh detected";
            return p;
        }
        p.type = "unknown";
        p.command = null;
        p.message = "No recognized build system. Add Makefile, package.json, setup.py or build.sh.";
        return p;
    }

    public static String runCommand(File projectDir, ZishuEnv env) {
        File runSh = new File(projectDir, "run.sh");
        if (runSh.isFile()) return "sh run.sh";
        File mainPy = new File(projectDir, "main.py");
        if (mainPy.isFile() && (ToolRegistry.findInPath(env, "python") != null
                || ToolRegistry.findInPath(env, "python3") != null)) {
            return "python main.py";
        }
        File indexJs = new File(projectDir, "index.js");
        if (indexJs.isFile() && ToolRegistry.findInPath(env, "node") != null) {
            return "node index.js";
        }
        return null;
    }
}