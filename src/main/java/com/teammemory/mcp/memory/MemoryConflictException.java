package com.teammemory.mcp.memory;

/**
 * RuntimeException — Spring AI converts this to an MCP error result conveyed
 * back to the calling agent, which is expected to re-read and retry.
 */
public class MemoryConflictException extends RuntimeException {

    public MemoryConflictException(String message) {
        super(message);
    }
}
