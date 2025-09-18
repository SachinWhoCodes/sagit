package com.sagit.commands;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
    name = "init",
    description = "Initializes the .sagit and/or .git repository based on existing setup.",
    mixinStandardHelpOptions = true
)
public class SagitInit implements Callable<Git> {

    @Option(
        names = {"-q", "--quiet"},
        description = "Be quiet, only report errors"
    )
    private boolean quiet;

    @Option(
        names = "--bare",
        description = "create a bare repo"
    )
    private boolean bare;

    @Option(
        names = "--template",
        paramLabel = "<template-directory>",
        description = "Directory containing templates for the new repository"
    )
    private File templateDirectory;

    @Option(
        names = "--separate-git-dir",
        paramLabel = "<git-dir>",
        description = "Separate the Git directory from the working tree"
    )
    private File gitDir;

    @Option(
        names = "--object-format",
        paramLabel = "<format>",
        description = "Specify the object format (e.g., 'sha1', 'sha256')"
    )
    private String objectFormat;

    @Option(
        names = "--ref-format",
        paramLabel = "<format>",
        description = "Specify the reference storage format (e.g., 'files')"
    )
    private String refFormat;

    @Option(
        names = {"-b", "--initial-branch"},
        paramLabel = "<branch-name>",
        description = "Specify the initial branch name (e.g., 'main')"
    )
    private String initialBranch;

    @Option(
        names = "--shared",
        paramLabel = "<permissions>",
        description = "Share the repository with a group; optional permissions (e.g., 'group', 'all', 'umask')",
        arity = "0..1",
        defaultValue = "false"
    )
    private String shared;

    @Parameters(
        index = "0",
        paramLabel = "<directory>",
        description = "The directory to initialize the repository in (defaults to current directory)",
        arity = "0..1"
    )
    private File directory;

    @Override
    public Git call() throws Exception {
        File baseDir = (directory != null) ? directory : new File(".");
        File gitDirPath = (this.gitDir != null) ? this.gitDir : new File(baseDir, ".git");
        File sagitDirPath = new File(baseDir, ".sagit");

        if (!quiet) {
            System.out.println("Checking directory: " + baseDir.getAbsolutePath());
        }

        // Check if .git already exists
        boolean gitExists = gitDirPath.exists() && gitDirPath.isDirectory();
        if (!quiet) {
            System.out.println(".git exists: " + gitExists);
        }

        InitCommand init = new InitCommand();
        init.setDirectory(baseDir);
        init.setBare(bare);
        if (this.gitDir != null) {
            init.setGitDir(this.gitDir);
        }
        if (initialBranch != null) {
            try {
                init.setInitialBranch(initialBranch);
            } catch (InvalidRefNameException e) {
                if (!quiet) {
                    System.err.println("Error: invalid branch name '" + initialBranch + "' : " + e.getMessage());
                }
                throw e;
            }
        }

        if (templateDirectory != null) {
            if (!quiet) {
                System.err.println("Warning: Sagit uses JGIT internally and JGit InitCommand does not support --template. " +
                                   "You may need to manually copy templates to the .git directory.");
            }
        }

        if (objectFormat != null) {
            if (!objectFormat.equals("sha1") && !objectFormat.equals("sha256")) {
                if (!quiet) {
                    System.err.println("Error: Unsupported object format '" + objectFormat + "'. Supported: 'sha1', 'sha256'");
                }
                throw new IllegalArgumentException("Unsupported object format: " + objectFormat);
            }
            if (!quiet) {
                System.err.println("Warning: JGit InitCommand does not fully support --object-format. Default is 'sha1'. For 'sha256', additional config may be needed post-init.");
            }
        }

        if (refFormat != null) {
            if (!refFormat.equals("files")) {
                if (!quiet) {
                    System.err.println("Error: Unsupported ref format '" + refFormat + "'. Supported: 'files'");
                }
                throw new IllegalArgumentException("Unsupported ref format: " + refFormat);
            }
            if (!quiet) {
                System.err.println("Warning: JGit InitCommand does not support --ref-format. Default is 'files'.");
            }
        }

        if (shared != null && !shared.equals("false")) {
            if (!quiet) {
                System.err.println("Warning: JGit InitCommand does not support --shared. You may need to manually configure permissions post-init.");
            }
        }

        Git git = null;
        if (!gitExists) {
            // Initialize .git if it doesn't exist
            try {
                git = init.call();
                if (!quiet) {
                    System.out.println("Initialized Git repository in " + baseDir.getAbsolutePath());
                }
            } catch (GitAPIException e) {
                if (!quiet) {
                    System.err.println("Error initializing .git: " + e.getMessage());
                }
                throw e;
            }
        } else {
            if (!quiet) {
                System.out.println("Existing .git found in " + baseDir.getAbsolutePath() + ". Skipping Git initialization.");
            }
        }

        // Initialize .sagit directory
        try {
            if (!sagitDirPath.exists()) {
                Files.createDirectories(sagitDirPath.toPath());
                Path configPath = sagitDirPath.toPath().resolve("config.txt");
                Files.writeString(configPath, "Sagit tracking configuration initialized on " + java.time.LocalDateTime.now());
                if (!quiet) {
                    System.out.println("Initialized .sagit directory in " + baseDir.getAbsolutePath());
                }
            } else {
                if (!quiet) {
                    System.out.println("Existing .sagit found in " + baseDir.getAbsolutePath() + ". Skipping .sagit initialization.");
                }
            }
        } catch (IOException e) {
            if (!quiet) {
                System.err.println("Error initializing .sagit: " + e.getMessage());
            }
            throw e;
        }

        return git; // Return null if .git wasn't initialized, or the Git object if it was
    }
}