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

@CommandLine.Command(name="commit-msg", description="Generate commit message draft if empty")
public class CommitMsgHookCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "Path to COMMIT_EDITMSG")
    Path msgFile;

    @Override public void run() {
        try {
            // Respect existing message
            if (Files.exists(msgFile) && !Files.readString(msgFile).trim().isEmpty()) return;

            try (GitService gs = GitService.openFromWorkingDir()) {
                List<DiffEntry> diffs = gs.diffStagedAgainstHead();

                // Basic file stats
                int add=0, mod=0, del=0;
                Set<String> scopes = new LinkedHashSet<>();
                int deltaClasses=0, deltaMethods=0;

                JavaSemanticAnalyzer analyzer = new JavaSemanticAnalyzer();

                for (DiffEntry de : diffs) {
                    switch (de.getChangeType()) {
                        case ADD -> add++;
                        case MODIFY -> mod++;
                        case DELETE -> del++;
                        default -> {}
                    }
                    String path = de.getChangeType() == DiffEntry.ChangeType.DELETE ? de.getOldPath() : de.getNewPath();
                    String scope = scopeFromPath(path);
                    if (scope != null) scopes.add(scope);

                    boolean isJava = path != null && path.endsWith(".java");
                    if (isJava) {
                        byte[] ob = (de.getOldId() != null && de.getOldId().toObjectId() != null) ? gs.loadBlob(de.getOldId().toObjectId()) : null;
                        byte[] nb = (de.getNewId() != null && de.getNewId().toObjectId() != null) ? gs.loadBlob(de.getNewId().toObjectId()) : null;
                        var os = analyzer.analyze(ob == null ? "" : new String(ob));
                        var ns = analyzer.analyze(nb == null ? "" : new String(nb));
                        var d  = ns.diff(os);
                        deltaClasses += d.classes + d.interfaces_ + d.enums_;
                        deltaMethods += d.methods;
                    }
                }

                String type = guessType(add, mod, del, scopes);
                String scope = scopes.isEmpty() ? "core" : String.join(",", scopes);

                String summary = switch (type) {
                    case "fix" -> "fix issue in " + scope;
                    case "docs" -> "update docs";
                    case "test" -> "update tests";
                    case "refactor" -> "refactor " + scope;
                    default -> "add/update " + scope;
                };

                String header = String.format("%s(%s): %s", type, scope, summary);

                String trailer = String.format(
                        "%n%n[sagit] files: +%d ~%d -%d; java delta: types=%d, methods=%d%n",
                        add, mod, del, deltaClasses, deltaMethods);

                Files.writeString(msgFile, header + trailer);
            }
        } catch (Exception e) {
            // Never block commit; just leave message empty
        }
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
        return path.split("/")[0];
    }

    private String guessType(int add, int mod, int del, Set<String> scopes) {
        if (scopes.stream().allMatch(s -> s.equals("docs"))) return "docs";
        if (scopes.stream().allMatch(s -> s.equals("test"))) return "test";
        if (del > 0 && add == 0) return "refactor";
        return (add > 0) ? "feat" : "chore";
    }
}
