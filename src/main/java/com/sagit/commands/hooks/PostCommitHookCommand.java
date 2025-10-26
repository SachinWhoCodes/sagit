package com.sagit.commands.hooks;

import com.sagit.git.GitService;
import com.sagit.meta.MetaRecord;
import com.sagit.meta.MetaStore;
import com.sagit.semantic.JavaSemanticAnalyzer;
import com.sagit.utils.FS; // change if your utils pkg differs

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

@CommandLine.Command(name = "post-commit", description = "Append metadata for the latest commit")
public class PostCommitHookCommand implements Runnable {

    @Override public void run() {
        try (GitService gs = GitService.openFromWorkingDir()) {
            RevCommit head = gs.headCommit();
            if (head == null) return;

            // First-commit safe parent handling
            RevCommit parent = head.getParentCount() > 0 ? head.getParent(0) : null;
            ObjectId aTree = (parent == null) ? ObjectId.zeroId() : parent.getTree();
            ObjectId bTree = head.getTree();

            List<DiffEntry> diffs = gs.diffBetween(aTree, bTree);

            int filesAdded = 0, filesModified = 0, filesDeleted = 0;
            int deltaTypes = 0, deltaMethods = 0;

            JavaSemanticAnalyzer analyzer = new JavaSemanticAnalyzer();

            for (DiffEntry de : diffs) {
                // Never throw on rename/copy – count them as modify
                switch (de.getChangeType()) {
                    case ADD    -> filesAdded++;
                    case MODIFY -> filesModified++;
                    case RENAME -> filesModified++;
                    case COPY   -> filesModified++;
                    case DELETE -> filesDeleted++;
                    default     -> filesModified++;
                }

                String path = de.getChangeType() == DiffEntry.ChangeType.DELETE ? de.getOldPath() : de.getNewPath();
                if (path != null && path.endsWith(".java")) {
                    // GUARDED loads – skip zero/absent blobs
                    byte[] ob = loadIfPresent(gs, de, true);
                    byte[] nb = loadIfPresent(gs, de, false);

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
            rec.timestamp = Instant.now().toString();
            rec.summary   = summary;

            Path root = FS.repoRoot();
            Files.createDirectories(root.resolve(".sagit")); // ensure folder
            new MetaStore(root.resolve(".sagit/meta.jsonl")).append(rec);

            System.out.println("[sagit] post-commit: metadata appended");
        } catch (Exception e) {
            // Show cause in .sagit/hook.log so we can diagnose if anything else happens
            e.printStackTrace();
        }
    }

    /** Return blob bytes for the selected side, or null if the object id is zero/absent. */
    private static byte[] loadIfPresent(GitService gs, DiffEntry de, boolean oldSide) throws Exception {
        var abbr = oldSide ? de.getOldId() : de.getNewId();
        if (abbr == null) return null;
        ObjectId oid;
        try {
            oid = abbr.toObjectId();
        } catch (IllegalArgumentException ex) {
            return null; // invalid/abbrev id
        }
        if (oid == null || ObjectId.zeroId().equals(oid)) return null;
        return gs.loadBlob(oid);
    }
}
