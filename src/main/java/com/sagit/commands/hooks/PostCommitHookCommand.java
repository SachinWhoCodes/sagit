package com.sagit.commands.hooks;

import com.sagit.git.GitService;
import com.sagit.meta.MetaRecord;
import com.sagit.meta.MetaStore;
import com.sagit.semantic.JavaSemanticAnalyzer;
import com.sagit.utils.FS; // if your helper is in com.sagit.utils.FS, change this import

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CommandLine.Command(
        name = "post-commit",
        description = "Append metadata for the latest commit")
public class PostCommitHookCommand implements Runnable {

    @Override
    public void run() {
        // Never block the user's commit even if something goes wrong.
        try (GitService gs = GitService.openFromWorkingDir()) {
            RevCommit head = gs.headCommit();
            if (head == null) return;

            // Handle first commit (no parent): pass zeroId so GitService uses EmptyTreeIterator.
            RevCommit parent = head.getParentCount() > 0 ? head.getParent(0) : null;
            ObjectId aTree = (parent == null) ? ObjectId.zeroId() : parent.getTree();
            ObjectId bTree = head.getTree();

            List<DiffEntry> diffs = gs.diffBetween(aTree, bTree);

            int filesAdded = 0, filesModified = 0, filesDeleted = 0;
            int deltaTypes = 0, deltaMethods = 0;

            JavaSemanticAnalyzer analyzer = new JavaSemanticAnalyzer();

            for (DiffEntry de : diffs) {
                // Be generous: treat RENAME/COPY as MODIFY so we never throw or miss metadata.
                switch (de.getChangeType()) {
                    case ADD    -> filesAdded++;
                    case MODIFY -> filesModified++;
                    case RENAME -> filesModified++;
                    case COPY   -> filesModified++;
                    case DELETE -> filesDeleted++;
                    default     -> filesModified++;
                }

                String path = de.getChangeType() == DiffEntry.ChangeType.DELETE
                        ? de.getOldPath()
                        : de.getNewPath();

                // Java-specific semantic counters
                if (path != null && path.endsWith(".java")) {
                    byte[] ob = (de.getOldId() != null && de.getOldId().toObjectId() != null)
                            ? gs.loadBlob(de.getOldId().toObjectId())
                            : null;
                    byte[] nb = (de.getNewId() != null && de.getNewId().toObjectId() != null)
                            ? gs.loadBlob(de.getNewId().toObjectId())
                            : null;

                    var os = analyzer.analyze(ob == null ? "" : new String(ob));
                    var ns = analyzer.analyze(nb == null ? "" : new String(nb));
                    var d  = ns.diff(os);
                    deltaTypes   += d.classes + d.interfaces_ + d.enums_;
                    deltaMethods += d.methods;
                }
            }

            Map<String, Integer> summary = new HashMap<>();
            summary.put("files_added", filesAdded);
            summary.put("files_modified", filesModified);
            summary.put("files_deleted", filesDeleted);
            summary.put("java_types_delta", deltaTypes);
            summary.put("java_methods_delta", deltaMethods);

            MetaRecord rec = new MetaRecord();
            rec.commitId  = head.getId().name();
            rec.timestamp = Instant.now();
            rec.summary   = summary;

            Path root = FS.repoRoot();
            Files.createDirectories(root.resolve(".sagit")); // ensure folder for first write
            MetaStore store = new MetaStore(root.resolve(".sagit/meta.jsonl"));
            store.append(rec);

        } catch (Exception ignored) {
            // swallow — hooks must never fail the user's commit
        }
    }
}
