package com.sagit.commands;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;

public class InitCommand {

    public static void init() {
        File gitFolder = new File(".git");
        File sagitFolder = new File(".sagit");

        try {
            if (!gitFolder.exists()) {
                // Initialize a new Git repo
                Git.init()
                   .setDirectory(new File(System.getProperty("user.dir")))
                   .call();
                System.out.println("Initialized empty Git repository in " + gitFolder.getAbsolutePath());
            } else {
                System.out.println(".git folder already exists.");
            }

            // Now create .sagit folder if not exists
            if (!sagitFolder.exists()) {
                boolean created = sagitFolder.mkdir();
                if (created) {
                    System.out.println("Created .sagit folder for Sagit metadata.");
                } else {
                    System.err.println("Failed to create .sagit folder.");
                }
            } else {
                System.out.println(".sagit folder already exists.");
            }
        } catch (GitAPIException e) {
            System.err.println("Error initializing Git repository: " + e.getMessage());
        }
    }
}
