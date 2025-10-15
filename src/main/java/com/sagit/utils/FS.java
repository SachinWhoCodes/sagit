package com.sagit.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

public class FS {
    public static Path repoRoot() throws IOException, InterruptedException {
        Process p = new ProcessBuilder("git", "rev-parse", "--show-toplevel")
                .redirectErrorStream(true).start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line = br.readLine();
            p.waitFor();
            if (line == null || line.isBlank()) {
                throw new IOException("Not a git repository (or no git in PATH)");
            }
            return Paths.get(line).toAbsolutePath().normalize();
        }
    }

    public static Path ensureDir(Path dir) throws IOException {
        Files.createDirectories(dir);
        return dir;
    }

    public static void writeExecutable(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
            );
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException ignored) { /* Windows */ }
    }

    public static void copy(Path from, Path to) throws IOException {
        ensureDir(to.getParent());
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    public static Path jarSelf() {
        return Paths.get(SagitJarLocator.locate());
    }

    // Separate nested class to avoid security manager issues
    static class SagitJarLocator {
        static String locate() {
            try {
                return new java.io.File(
                        com.sagit.SagitCLI.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI()
                ).getAbsolutePath();
            } catch (Exception e) {
                return "";
            }
        }
    }
}
