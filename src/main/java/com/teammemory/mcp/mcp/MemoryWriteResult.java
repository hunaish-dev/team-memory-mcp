package com.teammemory.mcp.mcp;

import com.teammemory.mcp.memory.MemoryEntry;

public record MemoryWriteResult(String path, long version) {

    static MemoryWriteResult from(MemoryEntry entry) {
        return new MemoryWriteResult(entry.getPath(), entry.getVersion());
    }
}
