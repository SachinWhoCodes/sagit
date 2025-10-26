package com.sagit.git;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class GitService implements Closeable, AutoCloseable {

    private final Repository repo;

    private GitService(Repository repo) {
        this.repo = repo;
    }

    public static GitService openFromWorkingDir() throws IOException {
        FileRepositoryBuilder b = new FileRepositoryBuilder()
                .setWorkTree(new File("."))
                .findGitDir(new File("."))
                .readEnvironment();

        Repository r = b.build();
        return new GitService(r);
    }

    public Repository repo() { return repo; }

    /** Latest commit on HEAD (or null if none). */
    public RevCommit headCommit() throws IOException {
        ObjectId head = repo.resolve("HEAD");
        if (head == null) return null;
        try (RevWalk walk = new RevWalk(repo)) {
            return walk.parseCommit(head);
        }
    }

    /** Read blob bytes by id. */
    public byte[] loadBlob(ObjectId id) throws IOException {
        try (ObjectReader reader = repo.newObjectReader()) {
            ObjectLoader loader = reader.open(id);
            return loader.getBytes();
        }
    }

    /** Create a tree object for the current index (staged content). */
    public ObjectId writeIndexTree() throws IOException {
        DirCache index = DirCache.read(repo); // current index
        try (ObjectInserter ins = repo.newObjectInserter()) {
            ObjectId treeId = index.writeTree(ins);
            ins.flush();
            return treeId;
        }
    }

    /** Diff: STAGED vs HEAD (first-commit safe). */
    public List<DiffEntry> diffStagedAgainstHead() throws Exception {
        ObjectId indexTree = writeIndexTree();

        try (ObjectReader reader = repo.newObjectReader();
             DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream())) {
            df.setRepository(repo);
            df.setDetectRenames(true);

            AbstractTreeIterator oldIter;
            ObjectId headTree = repo.resolve("HEAD^{tree}");
            if (headTree == null) {
                oldIter = new EmptyTreeIterator(); // empty repo
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

    /** Diff: arbitrary trees (first-commit safe on oldTree). */
    public List<DiffEntry> diffBetween(ObjectId oldTree, ObjectId newTree) throws IOException {
        try (ObjectReader reader = repo.newObjectReader();
             DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream())) {
            df.setRepository(repo);
            df.setDetectRenames(true);

            AbstractTreeIterator aIter;
            if (oldTree == null || ObjectId.zeroId().equals(oldTree)) {
                aIter = new EmptyTreeIterator();
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

    @Override
    public void close() throws IOException {
        repo.close();
    }

    // Convenience: repo root for external code, if needed
    public Path workTree() {
        return repo.getWorkTree().toPath();
    }
}
