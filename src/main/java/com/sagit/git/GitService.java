package com.sagit.git;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

public class GitService implements AutoCloseable {
    private final Repository repo;
    private final Git git;

    public GitService(File workTree) throws IOException {
        this.repo = new FileRepositoryBuilder()
                .findGitDir(workTree)
                .build();
        this.git = new Git(repo);
    }

    public static GitService openFromWorkingDir() throws IOException {
        return new GitService(new File(".").getAbsoluteFile());
    }

    public ObjectId headCommitId() throws IOException {
        Ref head = repo.findRef("HEAD");
        if (head == null || head.getObjectId() == null) return null;
        return head.getObjectId();
    }

    public RevCommit headCommit() throws IOException {
        ObjectId id = headCommitId();
        if (id == null) return null;
        try (RevWalk walk = new RevWalk(repo)) {
            return walk.parseCommit(id);
        }
    }

    public List<DiffEntry> diffStagedAgainstHead() throws Exception {
        ObjectId head = repo.resolve("HEAD^{tree}");
        ObjectId indexTree = writeIndexTree();

        try (ObjectReader reader = repo.newObjectReader()) {
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            if (head != null) oldTreeIter.reset(reader, head);
            newTreeIter.reset(reader, indexTree);

            try (DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream())) {
                df.setRepository(repo);
                df.setDetectRenames(true);
                return df.scan(oldTreeIter, newTreeIter);
            }
        }
    }

    public List<DiffEntry> diffBetween(ObjectId oldTree, ObjectId newTree) throws IOException {
        try (ObjectReader reader = repo.newObjectReader();
             DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream())) {
            df.setRepository(repo);
            df.setDetectRenames(true);
            CanonicalTreeParser a = new CanonicalTreeParser();
            CanonicalTreeParser b = new CanonicalTreeParser();
            a.reset(reader, oldTree);
            b.reset(reader, newTree);
            return df.scan(a, b);
        }
    }

    public ObjectId writeIndexTree() throws Exception {
        ObjectInserter inserter = repo.newObjectInserter();
        try {
            DirCache index = repo.readDirCache();
            ObjectId treeId = index.writeTree(inserter);
            inserter.flush();
            return treeId;
        } finally {
            inserter.close();
        }
    }

    public byte[] loadBlob(ObjectId blobId) throws IOException {
        try (ObjectReader or = repo.newObjectReader()) {
            return or.open(blobId).getBytes();
        }
    }

    public byte[] loadPathAt(ObjectId treeId, String path) throws IOException {
        try (RevWalk rw = new RevWalk(repo)) {
            RevTree tree = rw.parseTree(treeId);
            try (org.eclipse.jgit.treewalk.TreeWalk tw = new org.eclipse.jgit.treewalk.TreeWalk(repo)) {
                tw.addTree(tree);
                tw.setRecursive(true);
                while (tw.next()) {
                    if (tw.getPathString().equals(path)) {
                        ObjectId blobId = tw.getObjectId(0);
                        return loadBlob(blobId);
                    }
                }
            }
        }
        return null;
    }

    public Repository repo() { return repo; }

    @Override public void close() { git.close(); repo.close(); }
}
