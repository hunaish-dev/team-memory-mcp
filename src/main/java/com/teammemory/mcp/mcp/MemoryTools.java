package com.teammemory.mcp.mcp;

import com.teammemory.mcp.memory.MemoryCategory;
import com.teammemory.mcp.memory.MemoryEntry;
import com.teammemory.mcp.memory.MemoryService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MemoryTools {

    private final MemoryService memoryService;

    public MemoryTools(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @McpTool(
            name = "memory_list",
            description = "List shared team memories, optionally filtered by category or path prefix. "
                    + "Call this before starting non-trivial work to see what the team already knows.")
    public List<MemorySummary> memoryList(
            @McpToolParam(description = "Filter by category: DECISION, CONVENTION, GOTCHA, or GLOSSARY. Omit to list all.", required = false)
            String category,
            @McpToolParam(description = "Filter by path prefix, e.g. '/decisions/'. Omit to list all. Ignored if category is set.", required = false)
            String pathPrefix) {
        MemoryCategory parsedCategory = (category == null || category.isBlank())
                ? null
                : MemoryCategory.valueOf(category.trim().toUpperCase());
        return memoryService.list(parsedCategory, pathPrefix).stream()
                .map(MemorySummary::from)
                .toList();
    }

    @McpTool(
            name = "memory_read",
            description = "Read the current content and version of one shared memory by path. "
                    + "Always call this before memory_write on an existing path.")
    public MemoryContent memoryRead(
            @McpToolParam(description = "Memory path, e.g. '/decisions/auth-migration.md'", required = true)
            String path) {
        return MemoryContent.from(memoryService.read(path));
    }

    @McpTool(
            name = "memory_write",
            description = "Create or update a shared memory. To update an existing memory, first call memory_read "
                    + "to get its current version and pass that as expectedVersion. Omit expectedVersion only when "
                    + "creating a brand-new path. On a version-conflict error, someone else wrote first — "
                    + "call memory_read again and retry.")
    public MemoryWriteResult memoryWrite(
            @McpToolParam(description = "Memory path, e.g. '/decisions/auth-migration.md'", required = true)
            String path,
            @McpToolParam(description = "Category: DECISION, CONVENTION, GOTCHA, or GLOSSARY", required = true)
            String category,
            @McpToolParam(description = "The memory content", required = true)
            String content,
            @McpToolParam(description = "The version last read via memory_read. Required when updating an existing memory; omit when creating a new one.", required = false)
            Long expectedVersion,
            @McpToolParam(description = "Identifier for who/what is writing, e.g. your username", required = true)
            String actor) {
        MemoryEntry entry = memoryService.write(
                path, MemoryCategory.valueOf(category.trim().toUpperCase()), content, expectedVersion, actor);
        return MemoryWriteResult.from(entry);
    }
}
