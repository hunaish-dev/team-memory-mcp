package com.teammemory.mcp.memory;

/** RuntimeException — Spring AI converts this to an MCP error result the calling agent can see. */
public class MemoryNotFoundException extends RuntimeException {

    public MemoryNotFoundException(String path) {
        super("No memory found at path: " + path);
    }
}
