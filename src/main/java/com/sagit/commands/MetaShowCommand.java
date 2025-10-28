package com.sagit.commands;

import com.sagit.utils.FS;
import picocli.CommandLine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CommandLine.Command(name = "meta", description = "Show/export Sagit metadata")
public class MetaShowCommand implements Runnable {

    @CommandLine.Parameters(index = "0", arity = "0..1",
            description = "What to show (last|all). Default: last",
            defaultValue = "last")
    String what;

    @CommandLine.Option(names = {"--export"}, description = "Export format: jsonl|csv")
    String export;

    @Override public void run() {
        try {
            Path f = FS.repoRoot().resolve(".sagit/meta.jsonl");
            if (!Files.exists(f)) { System.out.println("No metadata yet."); return; }

            if (export != null) {
                if ("jsonl".equalsIgnoreCase(export)) {
                    Files.lines(f, StandardCharsets.UTF_8).forEach(System.out::println);
                    return;
                } else if ("csv".equalsIgnoreCase(export)) {
                    exportCsv(f);
                    return;
                } else {
                    System.err.println("Unknown export format: " + export);
                    return;
                }
            }

            if ("all".equalsIgnoreCase(what)) {
                Files.lines(f, StandardCharsets.UTF_8).forEach(System.out::println);
                return;
            }

            // default: last
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i).trim();
                if (!line.isEmpty()) { System.out.println(line); return; }
            }
            System.out.println("No metadata yet.");
        } catch (Exception e) {
            System.err.println("meta failed: " + e.getMessage());
        }
    }

    // Very small CSV exporter for our known JSON shape
    private static void exportCsv(Path f) throws Exception {
        System.out.println("commitId,timestamp,files_added,files_modified,files_deleted,java_types_delta,java_methods_delta");
        Pattern cid = Pattern.compile("\"commitId\"\\s*:\\s*\"([^\"]+)\"");
        Pattern ts  = Pattern.compile("\"timestamp\"\\s*:\\s*\"([^\"]+)\"");
        Pattern fa  = Pattern.compile("\"files_added\"\\s*:\\s*(\\d+)");
        Pattern fm  = Pattern.compile("\"files_modified\"\\s*:\\s*(\\d+)");
        Pattern fd  = Pattern.compile("\"files_deleted\"\\s*:\\s*(\\d+)");
        Pattern jt  = Pattern.compile("\"java_types_delta\"\\s*:\\s*(\\d+)");
        Pattern jm  = Pattern.compile("\"java_methods_delta\"\\s*:\\s*(\\d+)");

        for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) continue;
            String c  = group(cid, line);
            String t  = group(ts,  line);
            String sfa = def(group(fa, line), "0");
            String sfm = def(group(fm, line), "0");
            String sfd = def(group(fd, line), "0");
            String sjt = def(group(jt, line), "0");
            String sjm = def(group(jm, line), "0");
            System.out.println(String.join(",", List.of(csv(c), csv(t), sfa, sfm, sfd, sjt, sjm)));
        }
    }
    private static String group(Pattern p, String s) { Matcher m = p.matcher(s); return m.find() ? m.group(1) : null; }
    private static String def(String v, String d) { return v == null ? d : v; }
    private static String csv(String v) { if (v == null) v = ""; if (v.contains(",") || v.contains("\"")) return "\"" + v.replace("\"","\"\"") + "\""; return v; }
}
