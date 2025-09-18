package com.sagit.commands;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheIterator; // Added
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.IndexDiffFilter;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk; // Added for OperationType
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/* Read official "git add" command docs here to understand every options and parameters | Link : https://git-scm.com/docs/git-add */

@Command(
    name = "add",
    description = "Update git index ( add any new or modified files to the index)",
    mixinStandardHelpOptions = true
)
public class SagitAdd implements Callable<Integer> {

    private final Repository repo;
    private final Git git;

    public SagitAdd(Repository repo) {
        if (repo == null) {
            throw new IllegalArgumentException("Repository cannot be null");
        }
        this.repo = repo;
        this.git = new Git(repo);
    }

    @Option(
        names = {"-n", "--dry-run"},
        description = "Don’t actually add the file(s), just show if they exist and/or will be ignored."
    )
    private boolean dryRun;

    @Option(
        names = {"-v", "--verbose"},
        description = "Be verbose"
    )
    private boolean verbose;

    @Option(
        names = {"-f", "--force"},
        description = "Allow adding otherwise ignored files."
    )
    private boolean force;

    @Option(
        names = "--sparse",
        description = "Allow updating index entries outside of the sparse-checkout cone. Normally, git add refuses to update index entries whose paths do not fit within the sparse-checkout cone, since those files might be removed from the working tree without warning."
    )
    private boolean sparse;

    @Option(
        names = {"i", "--interactive"},
        description = "Add modified contents in the working tree interactively to the index. Optional path arguments may be supplied to limit operation to a subset of the working tree. See “Interactive mode” for details."
    )
    private boolean interactive;

    @Option(
        names = {"-p", "--patch"},
        description = "Interactively choose hunks of patch between the index and the work tree and add them to the index. This gives the user a chance to review the difference before adding modified contents to the index."
    )
    private boolean patch;

    @Option(
        names = {"-e", "--edit"},
        description = "Open the diff vs. the index in an editor and let the user edit it. After the editor was closed, adjust the hunk headers and apply the patch to the index."
    )
    private boolean edit;

    @Option(
        names = {"-u", "--update"},
        description = "Update the index just where it already has an entry matching <pathspec>. This removes as well as modifies index entries to match the working tree, but adds no new files."
    )
    private boolean update;

    @Option(
        names = {"-A", "--all", "--no-ignore-removal"},
        description = "Update the index not only where the working tree has a file matching <pathspec> but also where the index already has an entry. This adds, modifies, and removes index entries to match the working tree."
    )
    private boolean noIgnoreRemoval;

    @Option(
        names = {"--no-all", "--ignore-removal"},
        description = "Update the index by adding new files that are unknown to the index and files modified in the working tree, but ignore files that have been removed from the working tree. This option is a no-op when no <pathspec> is used."
    )
    private boolean ignoreRemoval;

    @Option(
        names = {"-N", "--intent-to-add"},
        description = "Record only the fact that the path will be added later. An entry for the path is placed in the index with no content. This is useful for, among other things, showing the unstaged content of such files with git diff and committing them with git commit -a."
    )
    private boolean intentToAdd;

    @Option(
        names = "refresh",
        description = "Don’t add the file(s), but only refresh their stat() information in the index."
    )
    private boolean refresh;

    @Option(
        names = "--ignore-errors",
        description = "If some files could not be added because of errors indexing them, do not abort the operation, but continue adding the others. The command shall still exit with non-zero status. The configuration variable add.ignoreErrors can be set to true to make this the default behaviour."
    )
    private boolean ignoreErrors;

    @Option(
        names = "--ignore-missing",
        description = "This option can only be used together with --dry-run. By using this option the user can check if any of the given files would be ignored, no matter if they are already present in the work tree or not."
    )
    private boolean ignoreMissing;

    @Option(
        names = "--no-warn-embedded-repo",
        description = "By default, git add will warn when adding an embedded repository to the index without using git submodule add to create an entry in .gitmodules. This option will suppress the warning (e.g., if you are manually performing operations on submodules)."
    )
    private boolean noWarnEmbeddedRepo;

    @Option(
        names = "--renormalize",
        description = "Apply the 'clean' process freshly to all tracked files to forcibly add them again to the index. This is useful after changing core.autocrlf configuration or the text attribute in order to correct files added with wrong CRLF/LF line endings. This option implies -u. Lone CR characters are untouched, thus while a CRLF cleans to LF, a CRCRLF sequence is only partially cleaned to CRLF."
    )
    private boolean renormalize;

    @Option(
        names = "--chmod=(+|-)x",
        description = "Override the executable bit of the added files. The executable bit is only changed in the index, the files on disk are left unchanged."
    )
    private boolean chmod;

    @Parameters(description = "Pathspecs to add to the index")
    private List<String> filepatterns = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        if (update && noIgnoreRemoval) {
            throw new IllegalArgumentException(MessageFormat.format(
                    "Illegal combination of arguments: --update and --{0}",
                    noIgnoreRemoval ? "all/--no-ignore-removal" : "no-all/--ignore-removal"));
        }

        boolean addAll;
        if (filepatterns.isEmpty()) {
            if (update || noIgnoreRemoval != false || ignoreRemoval) {
                addAll = true;
            } else {
                throw new NoFilepatternException("At least one pattern is required");
            }
        } else {
            addAll = filepatterns.contains(".");
            if (!update && noIgnoreRemoval == false && !ignoreRemoval) {
                noIgnoreRemoval = true; // Default to --all behavior if not specified
            }
        }
        boolean stageDeletions = update || (noIgnoreRemoval && !ignoreRemoval);

        DirCache dc = null;
        try (ObjectInserter inserter = repo.newObjectInserter();
             NameConflictTreeWalk tw = new NameConflictTreeWalk(repo)) {
            tw.setOperationType(TreeWalk.OperationType.CHECKIN_OP); // Fixed with correct import
            dc = repo.lockDirCache();

            DirCacheBuilder builder = dc.builder();
            tw.addTree(new DirCacheBuildIterator(builder));
            WorkingTreeIterator workingTreeIterator = new FileTreeIterator(repo);
            workingTreeIterator.setDirCacheIterator(tw, 0);
            tw.addTree(workingTreeIterator);

            TreeFilter pathFilter = null;
            if (!addAll) {
                pathFilter = PathFilterGroup.createFromStrings(filepatterns);
            }
            if (!renormalize && pathFilter != null) {
                tw.setFilter(AndTreeFilter.create(new IndexDiffFilter(0, 1), pathFilter));
            } else if (pathFilter != null) {
                tw.setFilter(pathFilter);
            }

            byte[] lastAdded = null;
            while (tw.next()) {
                DirCacheIterator c = tw.getTree(0, DirCacheIterator.class); // Fixed with correct import
                WorkingTreeIterator f = tw.getTree(1, WorkingTreeIterator.class);
                if (c == null && f != null && f.isEntryIgnored() && !force) {
                    continue;
                } else if (c == null && update) {
                    continue;
                }

                DirCacheEntry entry = c != null ? c.getDirCacheEntry() : null;
                if (entry != null && entry.getStage() > 0 && lastAdded != null &&
                    lastAdded.length == tw.getPathLength() &&
                    tw.isPathPrefix(lastAdded, lastAdded.length) == 0) {
                    continue;
                }

                if (tw.isSubtree() && !tw.isDirectoryFileConflict()) {
                    tw.enterSubtree();
                    continue;
                }

                if (f == null) {
                    if (entry != null && (!stageDeletions || FileMode.GITLINK == entry.getFileMode())) {
                        builder.add(entry);
                    }
                    continue;
                }

                if (entry != null && entry.isAssumeValid()) {
                    builder.add(entry);
                    continue;
                }

                if ((f.getEntryRawMode() == FileMode.TYPE_TREE && f.getIndexFileMode(c) != FileMode.GITLINK) ||
                    (f.getEntryRawMode() == FileMode.TYPE_GITLINK && f.getIndexFileMode(c) == FileMode.TREE)) {
                    tw.enterSubtree();
                    continue;
                }

                byte[] path = tw.getRawPath();
                if (entry == null || entry.getStage() > 0) {
                    entry = new DirCacheEntry(path);
                }
                FileMode mode = f.getIndexFileMode(c);
                entry.setFileMode(mode);

                if (FileMode.GITLINK != mode) {
                    entry.setLength(f.getEntryLength());
                    entry.setLastModified(f.getEntryLastModifiedInstant());
                    long len = f.getEntryContentLength();
                    try (InputStream in = f.openEntryStream()) {
                        ObjectId id = inserter.insert(Constants.OBJ_BLOB, len, in); // Fixed to Constants.OBJ_BLOB
                        entry.setObjectId(id);
                    }
                } else {
                    entry.setLength(0);
                    entry.setLastModified(Instant.ofEpochSecond(0));
                    entry.setObjectId(f.getEntryObjectId());
                }
                builder.add(entry);
                lastAdded = path;
            }
            inserter.flush();
            if (!dryRun) {
                builder.commit();
            } else {
                System.out.println("Dry run: Would add files matching patterns: " + filepatterns);
            }
            return 0;
        } catch (IOException e) {
            throw new RuntimeException("Exception caught during execution of add command", e);
        } finally {
            if (dc != null) {
                dc.unlock();
            }
        }
    }
}