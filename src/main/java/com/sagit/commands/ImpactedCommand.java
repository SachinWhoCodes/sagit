package com.sagit.commands;

import com.sagit.config.Config;
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

@CommandLine.Command(name = "impacted", description = "List likely impacted tests since a ref")
public class ImpactedCommand implements Runnable {

    @CommandLine.Option(names = {"--since"}, defaultValue = "HEAD~1",
            description = "Compare this ref's tree to HEAD (default: ${DEFAULT-VALUE})")
    String since;

    @CommandLine.Option(names = {"--only-changed-tests"}, description = "Only list tests that actually exist")
    boolean onlyExisting;

    @Override public void run() {
        try (GitService gs = GitService.openFromWorkingDir()) {
            ObjectId toTree   = gs.repo().resolve("HEAD^{tree}");
            ObjectId fromTree = gs.repo().resolve(since + "^{tree}");
            if (toTree == null) { System.err.println("impacted: no HEAD"); return; }
            if (fromTree == null) { fromTree = ObjectId.zeroId(); } // first-commit safe

            List<DiffEntry> diffs = gs.diffBetween(fromTree, toTree);
            Set<String> tests = new LinkedHashSet<>();

            Config cfg = Config.load();
            Path rulesPath = FS.repoRoot().resolve(cfg.impactedRules);
            List<Rule> rules = loadRules(rulesPath);

            for (DiffEntry de : diffs) {
                String path = de.getChangeType()==DiffEntry.ChangeType.DELETE ? de.getOldPath() : de.getNewPath();
                if (path == null) continue;

                String mapped = applyRules(path, rules);
                if (mapped == null) mapped = defaultJavaMap(path);

                if (mapped != null) {
                    if (!onlyExisting || Files.exists(FS.repoRoot().resolve(mapped))) {
                        tests.add(mapped);
                    }
                }
            }

            if (tests.isEmpty()) System.out.println("(no obvious tests)");
            else tests.forEach(System.out::println);
        } catch (Exception e) {
            System.err.println("impacted failed: " + e.getMessage());
        }
    }

    // ---------- rules ----------
    private static List<Rule> loadRules(Path file) {
        if (!Files.exists(file)) return List.of();
        List<Rule> out = new ArrayList<>();
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=>", 2);
                if (parts.length != 2) continue;
                out.add(new Rule(Pattern.compile(parts[0].trim()), parts[1].trim()));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static String applyRules(String path, List<Rule> rules) {
        for (Rule r : rules) {
            var m = r.pattern.matcher(path);
            if (m.find()) return m.replaceAll(r.replacement);
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
