package com.sagit.commands;

import com.sagit.config.Config;
import com.sagit.git.GitService;
import com.sagit.semantic.JavaSemanticAnalyzer;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import picocli.CommandLine;

import java.util.*;

@CommandLine.Command(name = "describe", description = "Summarize changes since a ref")
public class DescribeCommand implements Runnable {

    @CommandLine.Option(names = {"--since"}, defaultValue = "HEAD~1",
            description = "Compare this ref's tree to HEAD (default: ${DEFAULT-VALUE})")
    String since;

    @CommandLine.Option(names = {"--format"}, defaultValue = "md",
            description = "Output format: md|json (default: ${DEFAULT-VALUE})")
    String format;

    @Override public void run() {
        try (GitService gs = GitService.openFromWorkingDir()) {
            ObjectId toTree   = gs.repo().resolve("HEAD^{tree}");
            ObjectId fromTree = gs.repo().resolve(since + "^{tree}");
            if (toTree == null) { System.err.println("describe: no HEAD"); return; }
            if (fromTree == null) { fromTree = ObjectId.zeroId(); } // first-commit safe

            List<DiffEntry> diffs = gs.diffBetween(fromTree, toTree);

            int add=0, mod=0, del=0;
            int deltaTypes=0, deltaMethods=0;
            Map<String,Integer> byLang = new LinkedHashMap<>();
            Map<String,Integer> byDir  = new LinkedHashMap<>();

            Config cfg = Config.load();
            boolean javaAllowed = cfg.languages.isEmpty() || cfg.languages.contains("java");
            JavaSemanticAnalyzer analyzer = javaAllowed ? new JavaSemanticAnalyzer() : null;

            for (DiffEntry de : diffs) {
                switch (de.getChangeType()) {
                    case ADD -> add++;
                    case MODIFY, RENAME, COPY -> mod++;
                    case DELETE -> del++;
                }

                String path = de.getChangeType() == DiffEntry.ChangeType.DELETE ? de.getOldPath() : de.getNewPath();
                if (path == null) continue;

                String lang = language(path);
                byLang.put(lang, byLang.getOrDefault(lang, 0) + 1);
                String dir = topDir(path);
                byDir.put(dir, byDir.getOrDefault(dir, 0) + 1);

                if (javaAllowed && path.endsWith(".java")) {
                    ObjectId oldObj = (de.getOldId() != null) ? de.getOldId().toObjectId() : null;
                    ObjectId newObj = (de.getNewId() != null) ? de.getNewId().toObjectId() : null;

                    byte[] ob = (oldObj != null && !ObjectId.zeroId().equals(oldObj)) ? gs.loadBlob(oldObj) : null;
                    byte[] nb = (newObj != null && !ObjectId.zeroId().equals(newObj)) ? gs.loadBlob(newObj) : null;

                    var os = analyzer.analyze(ob == null ? "" : new String(ob));
                    var ns = analyzer.analyze(nb == null ? "" : new String(nb));
                    var d  = ns.diff(os);
                    deltaTypes   += d.classes + d.interfaces_ + d.enums_;
                    deltaMethods += d.methods;
                }
            }

            if ("json".equalsIgnoreCase(format)) {
                // very small JSON (no external libs)
                System.out.println("{");
                System.out.println("  \"range\": {\"since\": \"" + escape(since) + "\", \"to\": \"HEAD\"},");
                System.out.println("  \"files\": {\"added\": " + add + ", \"modified\": " + mod + ", \"deleted\": " + del + "},");
                System.out.println("  \"java_delta\": {\"types\": " + deltaTypes + ", \"methods\": " + deltaMethods + "},");
                System.out.println("  \"by_language\": {");
                printMap(byLang, 4);
                System.out.println("  },");
                System.out.println("  \"by_dir\": {");
                printMap(byDir, 4);
                System.out.println("  }");
                System.out.println("}");
            } else {
                System.out.println("# Change Summary");
                System.out.println("- Range: `" + since + "` → `HEAD`");
                System.out.println("- Files: +" + add + " ~" + mod + " -" + del);
                System.out.println("- Java Δ: types=" + deltaTypes + ", methods=" + deltaMethods);
                if (!byLang.isEmpty()) {
                    System.out.println("\n## Files by language");
                    byLang.forEach((k,v) -> System.out.println("- " + k + ": " + v));
                }
                if (!byDir.isEmpty()) {
                    System.out.println("\n## Top-level directories touched");
                    byDir.forEach((k,v) -> System.out.println("- " + k + ": " + v));
                }
            }
        } catch (Exception e) {
            System.err.println("describe failed: " + e.getMessage());
        }
    }

    private static String language(String path) {
        int i = path.lastIndexOf('.');
        if (i < 0) return "other";
        String ext = path.substring(i+1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "java" -> "java";
            case "md"   -> "markdown";
            case "kt"   -> "kotlin";
            case "js"   -> "javascript";
            case "ts"   -> "typescript";
            case "xml"  -> "xml";
            default     -> ext;
        };
    }
    private static String topDir(String path) {
        int s = path.indexOf('/');
        return s > 0 ? path.substring(0, s) : ".";
    }
    private static String escape(String s) { return s.replace("\\","\\\\").replace("\"","\\\""); }
    private static void printMap(Map<String,Integer> m, int indent) {
        int i = 0, n = m.size();
        for (var e : m.entrySet()) {
            String comma = (++i < n) ? "," : "";
            System.out.println(" ".repeat(indent) + "\"" + escape(e.getKey()) + "\": " + e.getValue() + comma);
        }
    }
}
