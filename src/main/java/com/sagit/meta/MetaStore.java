package com.sagit.meta;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MetaStore {
    private final Path file;

    public MetaStore(Path file) {
        this.file = file;
    }

    public void append(MetaRecord rec) throws Exception {
        Files.createDirectories(file.getParent());
        String line = rec.toJson() + System.lineSeparator();
        Files.write(
                file,
                line.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE
        );
    }
}
