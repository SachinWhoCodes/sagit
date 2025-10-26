package com.sagit.meta;

import java.time.Instant;
import java.util.Map;

public class MetaRecord {
    public String commitId;
    public Instant timestamp;
    public Map<String, Integer> summary; // e.g., java_classes_added, java_methods_changed, files_added, files_modified, files_deleted
}
