package com.teammemory.mcp.mcp;

import com.teammemory.mcp.memory.MemoryCategory;
import com.teammemory.mcp.memory.MemoryEntry;

import java.time.Instant;

public record MemorySummary(String path, MemoryCategory category, long version, String updatedBy, Instant updatedAt) {

    static MemorySummary from(MemoryEntry entry) {
        return new MemorySummary(entry.getPath(), entry.getCategory(), entry.getVersion(), entry.getUpdatedBy(), entry.getUpdatedAt());
    }
}
