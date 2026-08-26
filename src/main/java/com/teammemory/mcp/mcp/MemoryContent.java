package com.teammemory.mcp.mcp;

import com.teammemory.mcp.memory.MemoryCategory;
import com.teammemory.mcp.memory.MemoryEntry;

public record MemoryContent(String path, MemoryCategory category, String content, long version) {

    static MemoryContent from(MemoryEntry entry) {
        return new MemoryContent(entry.getPath(), entry.getCategory(), entry.getContent(), entry.getVersion());
    }
}
