package com.sagit.commands;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import picocli.CommandLine.Command;

import java.io.File;

@Command(
    name = "init",
    description = "Initializes the .sagit and .git repository."
)
public class InitCommand implements Runnable {

    @Override
    public void run() {
        try {
            // 1. Initialize .git directory using JGit
            Git.init()
                .setDirectory(new File("."))
                .call();

            // 2. Also create a custom .sagit directory
            File sagitDir = new File(".sagit");
            if (!sagitDir.exists()) {
                boolean created = sagitDir.mkdir();
                if (created) {
                    System.out.println(".sagit directory created.");
                } else {
                    System.err.println("Failed to create .sagit directory.");
                }
            }

            System.out.println("Sagit initialized: .git and .sagit directories created.");
        } catch (GitAPIException e) {
            System.err.println("Error initializing Git repository: " + e.getMessage());
        }
    }
}
