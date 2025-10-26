package com.sagit.commands;

import com.sagit.SagitCLI;
import com.sagit.utils.FS;
import picocli.CommandLine;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@CommandLine.Command(
        name = "setup",
        description = "Install hooks and copy the runtime jar to .sagit/sagit.jar"
)
public class SetupCommand implements Runnable {

    @Override
    public void run() {
        try {
            final Path root = FS.repoRoot();                    // repo top-level
            final Path gitDir = root.resolve(".git");
            if (!Files.isDirectory(gitDir)) {
                throw new IllegalStateException("Not a git repository (no .git folder at " + root + ")");
            }

            // 1) copy the running jar to .sagit/sagit.jar
            final Path sagitDir = root.resolve(".sagit");
            Files.createDirectories(sagitDir);
            final Path destJar = sagitDir.resolve("sagit.jar");
            final Path srcJar  = runningJar();
            Files.copy(srcJar, destJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // 2) write hooks (unix + windows) with logging
            final Path hooks = gitDir.resolve("hooks");
            Files.createDirectories(hooks);

            // prepare-commit-msg
            String unixPrepare = """
                    #!/bin/sh
                    set -e
                    ROOT=$(git rev-parse --show-toplevel)
                    LOG="$ROOT/.sagit/hook.log"
                    JAR="$ROOT/.sagit/sagit.jar"
                    mkdir -p "$ROOT/.sagit"
                    echo "[sagit] $(date) prepare-commit-msg $1 $2 $3" >> "$LOG"
                    if [ ! -f "$JAR" ]; then echo "[sagit] JAR missing: $JAR" >> "$LOG"; exit 0; fi
                    exec java -jar "$JAR" hook prepare-commit-msg "$1" "$2" "$3" >> "$LOG" 2>&1
                    """;
            String winPrepare = """
                    @echo off
                    for /f "delims=" %%i in ('git rev-parse --show-toplevel') do set ROOT=%%i
                    set LOG=%ROOT%\\.sagit\\hook.log
                    set JAR=%ROOT%\\.sagit\\sagit.jar
                    if not exist "%ROOT%\\.sagit" mkdir "%ROOT%\\.sagit"
                    echo [sagit] %date% %time% prepare-commit-msg %1 %2 %3 >> "%LOG%"
                    if not exist "%JAR%" exit /b 0
                    java -jar "%JAR%" hook prepare-commit-msg %1 %2 %3 >> "%LOG%" 2>&1
                    """;

            // commit-msg
            String unixCommit = """
                    #!/bin/sh
                    set -e
                    ROOT=$(git rev-parse --show-toplevel)
                    LOG="$ROOT/.sagit/hook.log"
                    JAR="$ROOT/.sagit/sagit.jar"
                    mkdir -p "$ROOT/.sagit"
                    echo "[sagit] $(date) commit-msg $1" >> "$LOG"
                    if [ ! -f "$JAR" ]; then echo "[sagit] JAR missing: $JAR" >> "$LOG"; exit 0; fi
                    exec java -jar "$JAR" hook commit-msg "$1" >> "$LOG" 2>&1
                    """;
            String winCommit = """
                    @echo off
                    for /f "delims=" %%i in ('git rev-parse --show-toplevel') do set ROOT=%%i
                    set LOG=%ROOT%\\.sagit\\hook.log
                    set JAR=%ROOT%\\.sagit\\sagit.jar
                    if not exist "%ROOT%\\.sagit" mkdir "%ROOT%\\.sagit"
                    echo [sagit] %date% %time% commit-msg %1 >> "%LOG%"
                    if not exist "%JAR%" exit /b 0
                    java -jar "%JAR%" hook commit-msg %1 >> "%LOG%" 2>&1
                    """;

            // post-commit
            String unixPost = """
                    #!/bin/sh
                    set -e
                    ROOT=$(git rev-parse --show-toplevel)
                    LOG="$ROOT/.sagit/hook.log"
                    JAR="$ROOT/.sagit/sagit.jar"
                    mkdir -p "$ROOT/.sagit"
                    echo "[sagit] $(date) post-commit" >> "$LOG"
                    if [ ! -f "$JAR" ]; then echo "[sagit] JAR missing: $JAR" >> "$LOG"; exit 0; fi
                    exec java -jar "$JAR" hook post-commit >> "$LOG" 2>&1
                    """;
            String winPost = """
                    @echo off
                    for /f "delims=" %%i in ('git rev-parse --show-toplevel') do set ROOT=%%i
                    set LOG=%ROOT%\\.sagit\\hook.log
                    set JAR=%ROOT%\\.sagit\\sagit.jar
                    if not exist "%ROOT%\\.sagit" mkdir "%ROOT%\\.sagit"
                    echo [sagit] %date% %time% post-commit >> "%LOG%"
                    if not exist "%JAR%" exit /b 0
                    java -jar "%JAR%" hook post-commit >> "%LOG%" 2>&1
                    """;

            FS.writeExecutable(hooks.resolve("prepare-commit-msg"), unixPrepare);
            FS.writeExecutable(hooks.resolve("prepare-commit-msg.bat"), winPrepare);
            FS.writeExecutable(hooks.resolve("commit-msg"), unixCommit);
            FS.writeExecutable(hooks.resolve("commit-msg.bat"), winCommit);
            FS.writeExecutable(hooks.resolve("post-commit"), unixPost);
            FS.writeExecutable(hooks.resolve("post-commit.bat"), winPost);

            // 3) make sure we ignore internal artifacts
            final Path gitignore = root.resolve(".gitignore");
            ensureLine(gitignore, ".sagit/");
            ensureLine(gitignore, "sagit.jar");

            System.out.println("✅ Sagit hooks installed. Jar copied to .sagit/sagit.jar");
        } catch (Exception e) {
            System.err.println("setup failed: " + e.getMessage());
        }
    }

    private static void ensureLine(Path file, String line) throws IOException {
        if (Files.notExists(file)) {
            Files.writeString(file, line + System.lineSeparator());
            return;
        }
        List<String> all = Files.readAllLines(file);
        if (all.stream().noneMatch(l -> l.trim().equals(line))) {
            Files.writeString(file, String.join(System.lineSeparator(), all) + System.lineSeparator() + line + System.lineSeparator());
        }
    }

    /** Path to the currently-running jar (the CLI). */
    private static Path runningJar() throws URISyntaxException {
        var url = SagitCLI.class.getProtectionDomain().getCodeSource().getLocation();
        Path p = Path.of(url.toURI());
        if (Files.isDirectory(p)) {
            throw new IllegalStateException("Run setup from the built jar (not from classes directory).");
        }
        return p;
    }
}
