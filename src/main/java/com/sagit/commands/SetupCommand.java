package com.sagit.commands;

import java.nio.file.Path;

import com.sagit.utils.FS;

import picocli.CommandLine;

@CommandLine.Command(name = "setup", description = "Install hooks and prepare .sagit/")
public class SetupCommand implements Runnable {
    @Override public void run() {
        try {
            Path root = FS.repoRoot();
            Path sagitDir = FS.ensureDir(root.resolve(".sagit"));
            Path hooks = FS.ensureDir(root.resolve(".git/hooks"));

            // Copy current fat jar into .sagit/sagit.jar
            Path jar = FS.jarSelf();
            Path targetJar = sagitDir.resolve("sagit.jar");
            if (jar != null && !jar.toString().isBlank()) {
                FS.copy(jar, targetJar);
            }

            String unixPrepare = """
                    #!/bin/sh
                    ROOT=$(git rev-parse --show-toplevel)
                    JAR="$ROOT/.sagit/sagit.jar"
                    exec java -jar "$JAR" hook prepare-commit-msg "$1" "$2" "$3"
                    """;

            String winPrepare = """
                    @echo off
                    for /f "delims=" %%i in ('git rev-parse --show-toplevel') do set ROOT=%%i
                    set JAR=%ROOT%\\.sagit\\sagit.jar
                    java -jar "%JAR%" hook prepare-commit-msg %1 %2 %3
                    """;

            String unixCommitMsg = """
                    #!/bin/sh
                    ROOT=$(git rev-parse --show-toplevel)
                    JAR="$ROOT/.sagit/sagit.jar"
                    exec java -jar "$JAR" hook commit-msg "$1"
                    """;
            String unixPostCommit = """
                    #!/bin/sh
                    ROOT=$(git rev-parse --show-toplevel)
                    JAR="$ROOT/.sagit/sagit.jar"
                    exec java -jar "$JAR" hook post-commit
                    """;

            String winCommitMsg = """
                    @echo off
                    for /f "delims=" %%i in ('git rev-parse --show-toplevel') do set ROOT=%%i
                    set JAR=%ROOT%\\.sagit\\sagit.jar
                    java -jar "%JAR%" hook commit-msg %1
                    """;
            String winPostCommit = """
                    @echo off
                    for /f "delims=" %%i in ('git rev-parse --show-toplevel') do set ROOT=%%i
                    set JAR=%ROOT%\\.sagit\\sagit.jar
                    java -jar "%JAR%" hook post-commit
                    """;

            FS.writeExecutable(hooks.resolve("prepare-commit-msg"), unixPrepare);
            FS.writeExecutable(hooks.resolve("prepare-commit-msg.bat"), winPrepare);
            FS.writeExecutable(hooks.resolve("commit-msg"), unixCommitMsg);
            FS.writeExecutable(hooks.resolve("post-commit"), unixPostCommit);
            FS.writeExecutable(hooks.resolve("commit-msg.bat"), winCommitMsg);
            FS.writeExecutable(hooks.resolve("post-commit.bat"), winPostCommit);

            // .gitignore entry
            Path gi = root.resolve(".gitignore");
            String entry = System.lineSeparator() + ".sagit/" + System.lineSeparator();
            try {
                if (!java.nio.file.Files.exists(gi)) java.nio.file.Files.createFile(gi);
                String current = java.nio.file.Files.readString(gi);
                if (!current.contains(".sagit/")) {
                    java.nio.file.Files.writeString(gi, current + entry);
                }
            } catch (Exception ignored) {}

            System.out.println("✅ Sagit hooks installed. Jar copied to .sagit/sagit.jar");
        } catch (Exception e) {
            System.err.println("setup failed: " + e.getMessage());
            System.exit(2);
        }
    }
}
