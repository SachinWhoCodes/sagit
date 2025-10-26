package com.sagit.commands.hooks;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import com.sagit.git.GitService;
import com.sagit.meta.MetaRecord;
import com.sagit.meta.MetaStore;
import com.sagit.semantic.JavaSemanticAnalyzer;
import com.sagit.utils.FS;

import picocli.CommandLine;

@CommandLine.Command(name="post-commit", description="Record commit metadata")
public class PostCommitHookCommand implements Runnable {
    @Override public void run() {
        try (GitService gs = GitService.openFromWorkingDir()) {
            RevCommit head = gs.headCommit();
            if (head == null) return;

            RevCommit parent = head.getParentCount() > 0 ? head.getParent(0) : null;
            ObjectId a = parent == null ? ObjectId.zeroId() : parent.getTree();
            ObjectId b = head.getTree();

            List<DiffEntry> diffs = gs.diffBetween(a, b);

            int filesAdded=0, filesModified=0, filesDeleted=0;
            int deltaTypes=0, deltaMethods=0;

            JavaSemanticAnalyzer analyzer = new JavaSemanticAnalyzer();

            for (DiffEntry de : diffs) {
                switch (de.getChangeType()) {
                    case ADD -> filesAdded++;
                    case MODIFY -> filesModified++;
                    case DELETE -> filesDeleted++;
                }
                String path = de.getChangeType() == DiffEntry.ChangeType.DELETE ? de.getOldPath() : de.getNewPath();
                if (path != null && path.endsWith(".java")) {
                    byte[] ob = (de.getOldId() != null && de.getOldId().toObjectId() != null) ? gs.loadBlob(de.getOldId().toObjectId()) : null;
                    byte[] nb = (de.getNewId() != null && de.getNewId().toObjectId() != null) ? gs.loadBlob(de.getNewId().toObjectId()) : null;
                    var os = analyzer.analyze(ob == null ? "" : new String(ob));
                    var ns = analyzer.analyze(nb == null ? "" : new String(nb));
                    var d  = ns.diff(os);
                    deltaTypes += d.classes + d.interfaces_ + d.enums_;
                    deltaMethods += d.methods;
                }
            }

            Map<String,Integer> summary = new HashMap<>();
            summary.put("files_added", filesAdded);
            summary.put("files_modified", filesModified);
            summary.put("files_deleted", filesDeleted);
            summary.put("java_types_delta", deltaTypes);
            summary.put("java_methods_delta", deltaMethods);

            MetaRecord rec = new MetaRecord();
            rec.commitId = head.getName();
            rec.timestamp = Instant.ofEpochSecond(head.getCommitTime());
            rec.summary = summary;

            Path root = FS.repoRoot();
            MetaStore store = new MetaStore(root.resolve(".sagit/meta.jsonl"));
            store.append(rec);
        } catch (Exception ignored) { /* never block commit */ }
    }
}
