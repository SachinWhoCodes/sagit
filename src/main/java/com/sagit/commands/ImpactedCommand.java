package com.sagit.commands;

import com.sagit.git.GitService;
import com.sagit.utils.FS;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import picocli.CommandLine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

@CommandLine.Command(
        name = "impacted",
        description = "List likely impacted tests since a ref"
)
public class ImpactedCommand implements Runnable {

    @CommandLine.Option(names = {"--since"}, defaultValue = "HEAD~1",
            description = "Compare this ref's tree to HEAD (default: ${DEFAULT-VALUE})")
    String since;

    @Override public void run() {
        try (GitService gs = GitService.openFromWorkingDir()) {
            ObjectId toTree = gs.repo().resolve("HEAD^{tree}");
            ObjectId fromTree = gs.repo().resolve(since + "^{tree}");
            if (toTree == null) { System.err.println("impacted: no HEAD"); return; }
            if (fromTree == null) { fromTree = ObjectId.zeroId(); } // first-commit safe

            List<DiffEntry> diffs = gs.diffBetween(fromTree, toTree);
            Set<String> tests = new LinkedHashSet<>();

            // load .sagit/tests.map (optional)
            List<Rule> rules = loadRules(FS.repoRoot().resolve(".sagit/tests.map"));

            for (DiffEntry de : diffs) {
                String path = de.getChangeType()==DiffEntry.ChangeType.DELETE ? de.getOldPath() : de.getNewPath();
                if (path == null) continue;

                String mapped = applyRules(path, rules);
                if (mapped != null) {
                    tests.add(mapped);
                    continue;
                }

                // fallback heuristic for Java projects
                String maybe = defaultJavaMap(path);
                if (maybe != null) tests.add(maybe);
            }

            if (tests.isEmpty()) {
                System.out.println("(no obvious tests)");
            } else {
                tests.forEach(System.out::println);
            }
        } catch (Exception e) {
            System.err.println("impacted failed: " + e.getMessage());
        }
    }

    // ---------- rules ----------

    /** rules file format (regex => replacement), lines starting with # are comments.
     *  Example:
     *  ^src/main/java/(.*)\\.java$ => src/test/java/$1Test.java
     *  ^backend/(.*)\\.py$        => tests/$1_test.py
     */
    private static List<Rule> loadRules(Path file) {
        if (!Files.exists(file)) return List.of();
        List<Rule> out = new ArrayList<>();
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=>", 2);
                if (parts.length != 2) continue;
                String regex = parts[0].trim();
                String repl  = parts[1].trim();
                out.add(new Rule(Pattern.compile(regex), repl));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static String applyRules(String path, List<Rule> rules) {
        for (Rule r : rules) {
            String mapped = r.pattern.matcher(path).replaceAll(r.replacement);
            if (!mapped.equals(path)) return mapped;
        }
        return null;
    }

    private static String defaultJavaMap(String srcPath) {
        if (!srcPath.startsWith("src/main/java/") || !srcPath.endsWith(".java")) return null;
        String rest = srcPath.substring("src/main/java/".length(), srcPath.length() - ".java".length());
        return "src/test/java/" + rest + "Test.java";
    }

    private record Rule(Pattern pattern, String replacement) {}
}
