package com.sagit.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagit.utils.FS;

import picocli.CommandLine;

@CommandLine.Command(name="meta", description="Show metadata")
public class MetaShowCommand implements Runnable {

    @CommandLine.Parameters(index="0", description="commit id or 'last'", defaultValue = "last")
    String commit;

    @Override public void run() {
        try {
            Path root = FS.repoRoot();
            Path meta = root.resolve(".sagit/meta.jsonl");
            if (!Files.exists(meta)) {
                System.out.println("No metadata yet.");
                return;
            }
            ObjectMapper om = new ObjectMapper();
            try (Stream<String> lines = Files.lines(meta)) {
                var entries = lines
                        .map(s -> {
                            try { return om.readTree(s); } catch (Exception e) { return null; }
                        })
                        .filter(n -> n != null)
                        .toList();
                if (entries.isEmpty()) { System.out.println("No metadata."); return; }

                var node = "last".equals(commit) ? entries.get(entries.size()-1)
                                                 : entries.stream().filter(n -> commit.equals(n.get("commitId").asText())).findFirst().orElse(null);
                if (node == null) { System.out.println("Not found."); return; }
                System.out.println(om.writerWithDefaultPrettyPrinter().writeValueAsString(node));
            }
        } catch (Exception e) {
            System.err.println("meta show failed: " + e.getMessage());
            System.exit(2);
        }
    }
}
