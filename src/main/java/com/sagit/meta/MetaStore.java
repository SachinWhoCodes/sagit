package com.sagit.meta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class MetaStore {
    private final Path file;
    private static final ObjectWriter WRITER = new ObjectMapper().writer();

    public MetaStore(Path file) { this.file = file; }

    public void append(MetaRecord rec) throws IOException {
        Files.createDirectories(file.getParent());
        String json = WRITER.writeValueAsString(rec);
        Files.writeString(file, json + System.lineSeparator(),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }
}
