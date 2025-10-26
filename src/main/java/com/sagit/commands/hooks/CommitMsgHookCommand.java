package com.sagit.commands.hooks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.diff.DiffEntry;

import com.sagit.git.GitService;
import com.sagit.semantic.JavaSemanticAnalyzer;

import picocli.CommandLine;

@CommandLine.Command(name="commit-msg", description="Generate/fix commit message so it is never empty")
public class CommitMsgHookCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "Path to COMMIT_EDITMSG")
    Path msgFile;

    @Override public void run() {
        try {
            // If user already typed something meaningful, respect it.
            String current = Files.exists(msgFile) ? Files.readString(msgFile) : "";
            if (hasMeaningfulContent(current)) return;

            // Defaults in case analysis fails—this guarantees a non-empty message.
            String header = "chore: update";
            String trailer = "";

            try (GitService gs = GitService.openFromWorkingDir()) {
                List<DiffEntry> diffs = gs.diffStagedAgainstHead();

                int add = 0, mod = 0, del = 0;
                Set<String> scopes = new LinkedHashSet<>();
                int deltaTypes = 0, deltaMethods = 0;

                JavaSemanticAnalyzer analyzer = new JavaSemanticAnalyzer();

                for (DiffEntry de : diffs) {
                    switch (de.getChangeType()) {
                        case ADD -> add++;
                        case MODIFY -> mod++;
                        case DELETE -> del++;
                        default -> {}
                    }
                    String path = de.getChangeType() == DiffEntry.ChangeType.DELETE
                                  ? de.getOldPath() : de.getNewPath();

                    String scope = scopeFromPath(path);
                    if (scope != null && !scope.isBlank()) scopes.add(scope);

                    if (path != null && path.endsWith(".java")) {
                        byte[] ob = (de.getOldId() != null && de.getOldId().toObjectId() != null)
                                ? gs.loadBlob(de.getOldId().toObjectId()) : null;
                        byte[] nb = (de.getNewId() != null && de.getNewId().toObjectId() != null)
                                ? gs.loadBlob(de.getNewId().toObjectId()) : null;

                        var os = analyzer.analyze(ob == null ? "" : new String(ob));
                        var ns = analyzer.analyze(nb == null ? "" : new String(nb));
                        var d  = ns.diff(os);
                        deltaTypes   += d.classes + d.interfaces_ + d.enums_;
                        deltaMethods += d.methods;
                    }
                }

                // Build a nicer header if we could read diffs
                String type = guessType(add, mod, del, scopes);
                String scope = scopes.isEmpty() ? "core" : String.join(",", scopes);
                String summary = switch (type) {
                    case "fix"     -> "fix issue in " + scope;
                    case "docs"    -> "update docs";
                    case "test"    -> "update tests";
                    case "refactor"-> "refactor " + scope;
                    default        -> "add/update " + scope;
                };
                header = String.format("%s(%s): %s", type, scope, summary);

                trailer = String.format(
                        "%n%n[sagit] files: +%d ~%d -%d; java delta: types=%d, methods=%d%n",
                        add, mod, del, deltaTypes, deltaMethods);
            } catch (Exception ignored) {
                // Fall back to the generic header defined above.
            }

            // ALWAYS write something non-empty so the commit won't abort.
            Files.writeString(msgFile, header + trailer);

        } catch (Exception ignored) {
            // Last resort: still write a single-line header so Git accepts the commit.
            try { Files.writeString(msgFile, "chore: update\n"); } catch (Exception ignored2) {}
        }
    }

    // Treat only non-comment, non-empty lines as meaningful content.
    private boolean hasMeaningfulContent(String text) {
        if (text == null) return false;
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            return true;
        }
        return false;
    }

    private String scopeFromPath(String path) {
        if (path == null) return null;
        if (path.startsWith("src/test")) return "test";
        if (path.startsWith("docs/") || path.endsWith(".md")) return "docs";
        if (path.startsWith("src/main/java/")) {
            String rest = path.substring("src/main/java/".length());
            int slash = rest.indexOf('/');
            if (slash > 0) return rest.substring(0, slash).replace('.', '-');
            return "java";
        }
        String[] parts = path.split("/");
        return parts.length > 0 ? parts[0] : "core";
    }

    private String guessType(int add, int mod, int del, Set<String> scopes) {
        if (!scopes.isEmpty() && scopes.stream().allMatch("docs"::equals)) return "docs";
        if (!scopes.isEmpty() && scopes.stream().allMatch("test"::equals)) return "test";
        if (del > 0 && add == 0) return "refactor";
        return (add > 0) ? "feat" : "chore";
    }
}
