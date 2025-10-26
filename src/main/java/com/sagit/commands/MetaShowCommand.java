package com.sagit.commands;

import com.sagit.utils.FS;
import picocli.CommandLine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@CommandLine.Command(
        name = "meta",
        description = "Show metadata recorded by Sagit"
)
public class MetaShowCommand implements Runnable {

    @CommandLine.Parameters(index = "0", arity = "0..1",
            description = "What to show (last|all). Default: last",
            defaultValue = "last")
    String what;

    @Override
    public void run() {
        try {
            Path file = FS.repoRoot().resolve(".sagit/meta.jsonl");
            if (!Files.exists(file)) {
                System.out.println("No metadata yet.");
                return;
            }

            if ("all".equalsIgnoreCase(what)) {
                // print entire file
                Files.lines(file, StandardCharsets.UTF_8).forEach(System.out::println);
                return;
            }

            // default: last
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i).trim();
                if (!line.isEmpty()) {
                    System.out.println(line);
                    return;
                }
            }
            System.out.println("No metadata yet.");
        } catch (Exception e) {
            System.err.println("meta failed: " + e.getMessage());
        }
    }
}
