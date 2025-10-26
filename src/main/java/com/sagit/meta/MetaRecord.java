package com.sagit.meta;

import java.util.Map;

public class MetaRecord {
    public String commitId;
    public String timestamp;     // ISO-8601 string
    public Map<String, Integer> summary;

    // Minimal JSON without extra deps.
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"commitId\":\"").append(escape(commitId)).append("\",");
        sb.append("\"timestamp\":\"").append(escape(timestamp)).append("\",");
        sb.append("\"summary\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : summary.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(e.getKey())).append("\":").append(e.getValue());
        }
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
