package com.sagit.commands;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
// import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;

/* Read official "git commit" command docs here to understand every options and parameters | Link : https://git-scm.com/docs/git-commit */

@Command(
    name = "commit",
    description = "Record changes to the repository",
    mixinStandardHelpOptions = true
)
public class SagitCommit implements Callable<Integer> {

    private final Repository repo;
    private final Git git;

    public SagitCommit(Repository repo) {
        if (repo == null) {
            throw new IllegalArgumentException("Repository cannot be null");
        }
        this.repo = repo;
        this.git = new Git(repo);
    }

    @Option(names = {"-m", "--message"},
        description = "Commit message to be used. The specified file is not modified. Additional commit related message options can be used with this to indicate how the message should be prepared (see below)."
    )
    private String message;

    @Option(names = {"-F", "--file"},
        description = "Take the commit message from the given file. Use - to read from stdin. Additional commit related message options can be used with this to indicate how the message should be prepared (see below)."
    )
    private String file;

    @Option(names = {"-a", "--all", "--interactive"},
        description = "Tell the command to automatically stage files that have been modified and deleted, but new files you have not told Git about are not affected."
    )
    private boolean all;

    @Option(names = {"--amend"},
        description = "Replace the tip of the current branch by creating a new commit. The snapshot is taken from the current state of the working tree and index, with any changes that have been added to the index since the last commit being kept."
    )
    private boolean amend;

    @Option(names = {"-c", "--reedit-message"},
        description = "Like -C, but with -c the editor is invoked, so that the user can further edit the commit message."
    )
    private String reeditMessage;

    @Option(names = {"-C", "--reuse-message"},
        description = "Take an existing commit object, and reuse the log message and the authorship information (including the timestamp) when creating the commit."
    )
    private String reuseMessage;

    @Option(names = {"--fixup"},
        description = "Create a new commit which \"fixes up\" <commit> when applied with git rebase --autosquash."
    )
    private String fixup;

    @Option(names = {"--squash"},
        description = "Construct a commit message for use with git rebase --autosquash. The commit message title is taken from the specified commit with a prefix of \"squash! \"."
    )
    private String squash;

    @Option(names = {"--reset-author"},
        description = "When used with -C/-c/--amend options, declare that the authorship of the resulting commit now belongs to the committer."
    )
    private boolean resetAuthor;

    @Option(names = {"--author"},
        description = "Override the author name and email used in the commit (e.g., 'Name <email>')."
    )
    private String author;

    @Option(names = {"--date", "--date=relative"},
        description = "Override the author date used in the commit (e.g., '2025-09-18 10:01')."
    )
    private String date;

    @Option(names = {"-s", "--signoff"},
        description = "Add a Signed-off-by: line at the end of the commit message."
    )
    private boolean signoff;

    @Option(names = {"--no-verify"},
        description = "Bypasses the pre-commit and commit-msg hooks."
    )
    private boolean noVerify;

    @Option(names = {"--allow-empty-message"},
        description = "Allow the user to enter an empty commit message (forces an empty commit message)."
    )
    private boolean allowEmptyMessage;

    @Option(names = {"--allow-empty"},
        description = "Allow creating an empty commit."
    )
    private boolean allowEmpty;

    @Option(names = {"--dry-run"},
        description = "Do a dry run. This can be used to see what would be committed."
    )
    private boolean dryRun;

    @Option(names = {"--status"},
        description = "Include the output of git status in the commit message template."
    )
    private boolean status;

    @Option(names = {"-v", "--verbose"},
        description = "Show unified diff between the HEAD commit and the working tree."
    )
    private boolean verbose;

    @Option(names = {"-u", "--untracked-files"},
        description = "Show untracked files (modes: no, normal, all). Default is normal."
    )
    private String untrackedFiles = "normal";

    @Option(names = {"-i", "--interactive"},
        description = "Run the interactive mode (not fully supported, stages all changes)."
    )
    private boolean interactive;

    @Option(names = {"-p", "--patch"},
        description = "Use the interactive patch selection interface to choose which changes to commit (not fully supported, stages all changes)."
    )
    private boolean patch;

    @Option(names = {"--only"},
        description = "Commit only the specified paths."
    )
    private boolean only;

    @Option(names = {"--include"},
        description = "Commit the specified paths and any changes staged in the index."
    )
    private boolean include;

    @Option(names = {"--no-edit"},
        description = "Use the selected commit message without launching an editor."
    )
    private boolean noEdit;

    @Option(names = {"-S", "--gpg-sign"},
        description = "GPG-sign the commit with the default key or the key specified by -S<key-id>."
    )
    private String gpgSign;

    @Option(names = {"--no-gpg-sign"},
        description = "Countermand a previous --gpg-sign."
    )
    private boolean noGpgSign;

    @Option(names = {"--trailer"},
        description = "Specify a trailer to be added. Multiple -t options are allowed (e.g., 'Reviewed-by: Name <email>')."
    )
    private List<String> trailers = new ArrayList<>();

    @Parameters(description = "Pathspecs to commit only these files")
    private List<String> pathspecs = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        CommitCommand commit = git.commit();

        // Set author and committer
        PersonIdent committer = new PersonIdent(repo);
        PersonIdent authorIdent = amend ? getPreviousAuthor() : committer;

        if (author != null) {
            String[] authorParts = author.split("<|>");
            if (authorParts.length == 3) {
                authorIdent = new PersonIdent(authorParts[0].trim(), authorParts[1].trim());
            }
        }
        if (date != null) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                authorIdent = new PersonIdent(authorIdent.getName(), authorIdent.getEmailAddress(), sdf.parse(date), TimeZone.getTimeZone("IST"));
            } catch (ParseException e) {
                throw new IllegalArgumentException("Invalid date format. Use 'yyyy-MM-dd HH:mm', e.g., '2025-09-18 10:01'.");
            }
        }
        if (resetAuthor && (amend || reeditMessage != null || reuseMessage != null)) {
            authorIdent = committer;
        }

        commit.setAuthor(authorIdent);
        commit.setCommitter(committer);

        // Set message
        String commitMessage = determineCommitMessage();
        if (commitMessage == null && !allowEmptyMessage) {
            throw new IllegalArgumentException("Commit message must be provided with -m, -F, -c, -C, or --amend.");
        }
        if (commitMessage != null && !noEdit) {
            commitMessage = commitMessage.trim(); // Manual cleanup as JGit lacks CleanupMode
        }
        commit.setMessage(commitMessage);

        // Handle staging
        if (all) {
            git.add().addFilepattern(".").setUpdate(true).call();
        }
        if ((only || include) && !pathspecs.isEmpty()) {
            for (String path : pathspecs) {
                git.add().addFilepattern(path).call(); // Stage paths first
                commit.setOnly(path);
            }
        }

        // Signoff and trailers
        if (signoff) {
            commit.setMessage(commit.getMessage() + "\nSigned-off-by: " + committer.getName() + " <" + committer.getEmailAddress() + ">");
        }
        for (String trailer : trailers) {
            commit.setMessage(commit.getMessage() + "\n" + trailer);
        }

        // GPG signing
        if (gpgSign != null && !noGpgSign) {
            commit.setSign(true);
            commit.setSigningKey(gpgSign);
        } else if (noGpgSign) {
            commit.setSign(false);
        }

        // Dry run
        if (dryRun) {
            System.out.println("Dry run: Would commit with message: " + commit.getMessage());
            return 0;
        }

        // Verbose and status output
        if (verbose || status) {
            displayStatusAndDiff();
        }

        // Interactive and patch modes (disabled until fully supported)
        if (interactive || patch) {
            System.err.println("Interactive and patch modes are not fully supported. Staging all changes.");
            if (all) {
                git.add().addFilepattern(".").call();
            }
        }

        // Execute the commit
        try {
            RevCommit revCommit = commit.call();
            System.out.println("Commit successful. Commit hash: " + revCommit.getName());
            return 0;
        } catch (GitAPIException e) {
            System.err.println("Failed to commit: " + e.getMessage());
            return 1;
        }
    }

    private String determineCommitMessage() throws IOException {
        if (message != null) {
            return message;
        } else if (file != null) {
            return readMessageFromFile(file);
        } else if (reeditMessage != null || reuseMessage != null) {
            return readMessageFromCommit(reuseMessage != null ? reuseMessage : reeditMessage);
        } else if (amend) {
            return readMessageFromPreviousCommit();
        } else if (fixup != null) {
            return "fixup! " + getCommitMessage(fixup);
        } else if (squash != null) {
            return "squash! " + getCommitMessage(squash);
        }
        return null;
    }

    private String readMessageFromFile(String filePath) throws IOException {
        if ("-".equals(filePath)) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            return sb.toString().trim();
        }
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + filePath);
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.lines().reduce((a, b) -> a + "\n" + b).orElse("");
        }
    }

    private String readMessageFromCommit(String commitId) throws IOException {
        ObjectId commitObjectId = repo.resolve(commitId + "^{commit}");
        if (commitObjectId == null) throw new IllegalArgumentException("Commit not found: " + commitId);
        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit commit = walk.parseCommit(commitObjectId);
            return commit.getFullMessage();
        }
    }

    private String readMessageFromPreviousCommit() throws IOException {
        ObjectId headId = repo.resolve(Constants.HEAD + "^{commit}");
        if (headId == null) return null;
        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit commit = walk.parseCommit(headId);
            return commit.getFullMessage();
        }
    }

    private PersonIdent getPreviousAuthor() throws IOException {
        ObjectId headId = repo.resolve(Constants.HEAD + "^{commit}");
        if (headId == null) return new PersonIdent(repo);
        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit commit = walk.parseCommit(headId);
            return commit.getAuthorIdent();
        }
    }

    private String getCommitMessage(String commitId) throws IOException {
        ObjectId commitObjectId = repo.resolve(commitId + "^{commit}");
        if (commitObjectId == null) throw new IllegalArgumentException("Commit not found: " + commitId);
        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit commit = walk.parseCommit(commitObjectId);
            return commit.getShortMessage();
        }
    }

    private void displayStatusAndDiff() throws GitAPIException, IOException {
        Status status = git.status().call();
        System.out.println("=== Status ===");
        System.out.println("Modified: " + status.getModified());
        System.out.println("Added: " + status.getAdded());
        System.out.println("Removed: " + status.getRemoved());
        if ("all".equals(untrackedFiles) || "normal".equals(untrackedFiles)) {
            System.out.println("Untracked: " + status.getUntracked());
        }

        if (verbose) {
            ObjectId headId = repo.resolve(Constants.HEAD + "^{tree}");
            if (headId != null) {
                try (DiffFormatter df = new DiffFormatter(System.out)) {
                    df.setRepository(repo);
                    df.setDetectRenames(true);
                    // Use ObjectInserter to write the index tree
                    try (ObjectInserter oi = repo.newObjectInserter()) {
                        ObjectId indexTreeId = repo.readDirCache().writeTree(oi);
                        oi.flush();
                        List<DiffEntry> diffs = df.scan(headId, indexTreeId);
                        for (DiffEntry diff : diffs) {
                            df.format(diff);
                        }
                    }
                }
            }
        }
    }
}