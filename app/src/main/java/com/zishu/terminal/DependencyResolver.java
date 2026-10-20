package com.zishu.terminal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves package dependencies against a PackageDatabase. Reports
 * missing/conflicting/circular dependencies without modifying anything.
 */
public class DependencyResolver {

    public static class Result {
        public boolean ok;
        public List<String> installOrder = new ArrayList<String>();
        public List<String> missing = new ArrayList<String>();
        public List<String> cycles = new ArrayList<String>();
        public String message = "";
    }

    private final PackageDatabase db;

    public DependencyResolver(PackageDatabase db) { this.db = db; }

    public Result resolve(PackageInfo target) {
        Result r = new Result();
        if (target == null) { r.ok = false; r.message = "Null package"; return r; }

        Set<String> visiting = new HashSet<String>();
        Set<String> visited = new HashSet<String>();
        visit(target, visiting, visited, r);

        if (!r.missing.isEmpty()) {
            r.ok = false;
            r.message = "Missing dependencies: " + join(r.missing);
            return r;
        }
        if (!r.cycles.isEmpty()) {
            r.ok = false;
            r.message = "Circular dependency: " + join(r.cycles);
            return r;
        }
        r.ok = true;
        r.message = "Resolved";
        return r;
    }

    private void visit(PackageInfo p, Set<String> visiting, Set<String> visited, Result r) {
        if (p == null || p.name == null) return;
        if (visited.contains(p.name)) return;
        if (visiting.contains(p.name)) { r.cycles.add(p.name); return; }
        visiting.add(p.name);

        if (p.dependencies != null) {
            for (int i = 0; i < p.dependencies.length; i++) {
                String dep = p.dependencies[i];
                if (dep == null || dep.length() == 0) continue;
                PackageInfo d = db.byName(dep);
                if (d == null) {
                    r.missing.add(dep);
                } else {
                    visit(d, visiting, visited, r);
                }
            }
        }
        visiting.remove(p.name);
        visited.add(p.name);
        r.installOrder.add(p.name);
    }

    private static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}