package com.sagit.commands;

import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;

import com.sagit.git.GitService;
import com.sagit.semantic.JavaSemanticAnalyzer;

import picocli.CommandLine;

@CommandLine.Command(name = "diff", description = "Show semantic diff summary")
public class DiffSemanticCommand implements Runnable {

    @CommandLine.Option(names = "--semantic", description = "Enable semantic summary (Java)")
    boolean semantic = true;

    @CommandLine.Option(names = "--since", description = "Compare since ref (e.g., HEAD~1)")
    String since;

    @Override public void run() {
        try (GitService gs = GitService.openFromWorkingDir()) {
            List<DiffEntry> diffs;
            if (since != null && !since.isBlank()) {
                ObjectId a = gs.repo().resolve(since + "^{tree}");
                ObjectId b = gs.writeIndexTree(); // current index tree (or use HEAD if you prefer commits only)
                diffs = gs.diffBetween(a, b);
            } else {
                diffs = gs.diffStagedAgainstHead();
            }

            int filesAdded=0, filesModified=0, filesDeleted=0;
            int deltaClasses=0, deltaInterfaces=0, deltaEnums=0, deltaMethods=0, deltaFields=0;

            JavaSemanticAnalyzer analyzer = new JavaSemanticAnalyzer();

            for (DiffEntry de : diffs) {
                switch (de.getChangeType()) {
                    case ADD -> filesAdded++;
                    case MODIFY -> filesModified++;
                    case DELETE -> filesDeleted++;
                    default -> {}
                }

                if (!semantic) continue;
                String pathNew = de.getNewPath();
                String pathOld = de.getOldPath();
                boolean isJava = (pathNew != null && pathNew.endsWith(".java"))
                              || (pathOld != null && pathOld.endsWith(".java"));

                if (!isJava) continue;

                byte[] oldBytes = null, newBytes = null;
                if (de.getOldMode() != null && de.getOldId() != null && de.getOldId().toObjectId() != null) {
                    oldBytes = gs.loadBlob(de.getOldId().toObjectId());
                }
                if (de.getNewMode() != null && de.getNewId() != null && de.getNewId().toObjectId() != null) {
                    newBytes = gs.loadBlob(de.getNewId().toObjectId());
                }

                var oldStats = analyzer.analyze(oldBytes == null ? "" : new String(oldBytes));
                var newStats = analyzer.analyze(newBytes == null ? "" : new String(newBytes));
                var diff = newStats.diff(oldStats);
                deltaClasses    += diff.classes;
                deltaInterfaces += diff.interfaces_;
                deltaEnums      += diff.enums_;
                deltaMethods    += diff.methods;
                deltaFields     += diff.fields;
            }

            System.out.printf("Files: +%d ~%d -%d%n", filesAdded, filesModified, filesDeleted);
            if (semantic) {
                System.out.printf("Java: Δclasses=%d, Δinterfaces=%d, Δenums=%d, Δmethods=%d, Δfields=%d%n",
                        deltaClasses, deltaInterfaces, deltaEnums, deltaMethods, deltaFields);
            }
        } catch (Exception e) {
            System.err.println("diff failed: " + e.getMessage());
            System.exit(2);
        }
    }
}
