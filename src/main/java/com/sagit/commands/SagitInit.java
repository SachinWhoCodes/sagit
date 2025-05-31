package com.sagit.commands;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.concurrent.Callable;

@Command(
    name = "init",
    description = "Initializes the .sagit and .git repository.",
    mixinStandardHelpOptions = true
)
public class SagitInit implements Callable<Git> {

    // Setting up options (picocli)
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

    // parameters (picocli feature)
    @Parameters(
        index = "0",
        paramLabel = "<directory>",
        description = "The directory to initialize the repository in (defaults to current directory)",
        arity = "0..1"
    )
    private File directory;



    // call function that returns Git object
    @Override
    public Git call() throws Exception{

        // create the Jgit Init object to configure
        InitCommand init = new InitCommand();

        // The directory to initialize the repository in (defaults to current directory)
        if(directory != null){
            init.setDirectory(directory);
        }

        // set bare
        init.setBare(bare);


        // Set git directory
        if(gitDir != null){
            init.setGitDir(gitDir);
        }


        // Set initial branch
        if(initialBranch != null){
            try{
                init.setInitialBranch(initialBranch);
            } catch(InvalidRefNameException e){
                if(!quiet){
                    System.err.println("Error: invalid branch name '" + initialBranch + "' : " + e.getMessage() );
                }
                throw e;
            }
        }


        // Template directory, as Jgit does not support template directory, we are just showing a message to let the user know
        if(templateDirectory != null){
            if(!quiet){
                System.err.println("Warning: Sagit uses JGIT internally to manage git functionality and JGit InitCommand does not support --template. " +"You may need to manually copy templates to the .git directory.");
            }
        }


        // Handle object format (not directly supported by InitCommand)
        if (objectFormat != null) {
            if (!objectFormat.equals("sha1") && !objectFormat.equals("sha256")) {
                if (!quiet) {
                    System.err.println("Error: Unsupported object format '" + objectFormat + "'. " + "Supported: 'sha1', 'sha256'");
                }
                throw new IllegalArgumentException("Unsupported object format: " + objectFormat);
            }
            if (!quiet) {
                System.err.println("Warning: Sagit uses JGIT internally to manage git functionality and JGit InitCommand does not fully support --object-format. " + "Default is 'sha1'. For 'sha256', additional config may be needed post-init.");
            }
        }

        // Handle ref format (not directly supported by InitCommand)
        if (refFormat != null) {
            if (!refFormat.equals("files")) {
                if (!quiet) {
                    System.err.println("Error: Unsupported ref format '" + refFormat + "'. " +
                                       "Supported: 'files'");
                }
                throw new IllegalArgumentException("Unsupported ref format: " + refFormat);
            }
            if (!quiet) {
                System.err.println("Warning: Sagit uses JGIT internally to manage git functionality and JGit InitCommand does not support --ref-format. " + "Default is 'files'.");
            }
        }

        // Handle shared (not directly supported by InitCommand)
        if (shared != null && !shared.equals("false")) {
            if (!quiet) {
                System.err.println("Warning: Sagit uses JGIT internally to manage git functionality and JGit InitCommand does not support --shared. " + "You may need to manually configure permissions post-init.");
            }
        }


        // Final execution
        try{
            Git git = init.call();
            if(!quiet){
                System.out.println("Initialized " + (bare ? "bare " : "") + "Git repository in " + (directory != null ? directory.getAbsolutePath() : new File(".").getAbsolutePath()));
            }
            return git;
        } catch(GitAPIException e){
            if(!quiet){
                System.err.println("Error initializing repository: " + e.getMessage());
            }
            throw e;
        }
    }
}
