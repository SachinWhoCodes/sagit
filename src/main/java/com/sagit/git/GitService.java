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
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;


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
        ObjectId indexTree = writeIndexTree();

        try (ObjectReader reader = repo.newObjectReader();
            DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream())) {
            df.setRepository(repo);
            df.setDetectRenames(true);

            AbstractTreeIterator oldIter;
            ObjectId headTree = repo.resolve("HEAD^{tree}");
            if (headTree == null) {
                oldIter = new EmptyTreeIterator();         // ← proper empty repo handler
            } else {
                CanonicalTreeParser o = new CanonicalTreeParser();
                o.reset(reader, headTree);
                oldIter = o;
            }

            CanonicalTreeParser newIter = new CanonicalTreeParser();
            newIter.reset(reader, indexTree);

            return df.scan(oldIter, newIter);
        }
    }


    public List<DiffEntry> diffBetween(ObjectId oldTree, ObjectId newTree) throws IOException {
        try (ObjectReader reader = repo.newObjectReader();
            DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream())) {
            df.setRepository(repo);
            df.setDetectRenames(true);

            AbstractTreeIterator aIter;
            if (oldTree == null || ObjectId.zeroId().equals(oldTree)) {
                aIter = new EmptyTreeIterator();           // ← handle parentless (first) commit
            } else {
                CanonicalTreeParser a = new CanonicalTreeParser();
                a.reset(reader, oldTree);
                aIter = a;
            }

            CanonicalTreeParser b = new CanonicalTreeParser();
            b.reset(reader, newTree);

            return df.scan(aIter, b);
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
