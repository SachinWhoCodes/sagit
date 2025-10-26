package com.sagit.commands;

import com.sagit.git.GitService;
import com.sagit.semantic.JavaSemanticAnalyzer;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import picocli.CommandLine;

import java.util.*;

@CommandLine.Command(
        name = "describe",
        description = "Summarize changes since a ref (Markdown to stdout)"
)
public class DescribeCommand implements Runnable {

    @CommandLine.Option(names = {"--since"}, defaultValue = "HEAD~1",
            description = "Compare this ref's tree to HEAD (default: ${DEFAULT-VALUE})")
    String since;

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

            JavaSemanticAnalyzer analyzer = new JavaSemanticAnalyzer();

            for (DiffEntry de : diffs) {
                switch (de.getChangeType()) {
                    case ADD -> add++;
                    case MODIFY, RENAME, COPY -> mod++;
                    case DELETE -> del++;
                }

                String path = de.getChangeType() == DiffEntry.ChangeType.DELETE ? de.getOldPath() : de.getNewPath();
                if (path == null) continue;

                // language & top-level dir buckets
                String lang = language(path);
                byLang.put(lang, byLang.getOrDefault(lang, 0) + 1);
                String dir = topDir(path);
                byDir.put(dir, byDir.getOrDefault(dir, 0) + 1);

                // Java semantic summary – guard against zero/absent blobs
                if (path.endsWith(".java")) {
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

            // Markdown output
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
}
