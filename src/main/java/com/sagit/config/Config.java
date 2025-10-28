package com.sagit.config;

import com.sagit.utils.FS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Config {
    public String commitTemplate = null;              // optional single-line override
    public String impactedRules = ".sagit/tests.map"; // default path
    public Set<String> languages = Set.of();          // e.g., ["java"] to filter semantic ops

    public static Config load() throws IOException, InterruptedException {
        Path root = FS.repoRoot();
        Path f = root.resolve(".sagit/config.json");
        Config c = new Config();
        if (!Files.exists(f)) return c;

        try {
            String json = Files.readString(f, StandardCharsets.UTF_8);
            // minimal parsing (no external deps). tolerate missing fields.
            c.commitTemplate = extractString(json, "commitTemplate", null);
            c.impactedRules  = extractString(json, "impactedRules", c.impactedRules);
            String langs = extractArray(json, "languages"); // comma-separated raw list
            if (langs != null && !langs.isBlank()) {
                Set<String> s = new LinkedHashSet<>();
                for (String tok : langs.split(",")) {
                    String t = tok.trim();
                    if (!t.isEmpty()) s.add(t.replace("\"",""));
                }
                c.languages = s;
            }
        } catch (Exception ignored) {}
        return c;
    }

    private static String extractString(String json, String key, String dflt) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return dflt;
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return dflt;
        int q1 = json.indexOf('"', colon + 1);
        int q2 = (q1 >= 0) ? json.indexOf('"', q1 + 1) : -1;
        if (q1 < 0 || q2 < 0) return dflt;
        return json.substring(q1 + 1, q2);
    }

    // returns inner raw content of the array (e.g., "\"java\",\"ts\""), or null
    private static String extractArray(String json, String key) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return null;
        int b1 = json.indexOf('[', colon + 1);
        int b2 = (b1 >= 0) ? json.indexOf(']', b1 + 1) : -1;
        if (b1 < 0 || b2 < 0) return null;
        return json.substring(b1 + 1, b2).trim();
    }
}
