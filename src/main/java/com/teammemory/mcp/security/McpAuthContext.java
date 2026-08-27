package com.teammemory.mcp.security;

/** Shared keys for handing the authenticated actor from the servlet filter through to MCP tool methods. */
public final class McpAuthContext {

    /** Request attribute set by {@link ApiKeyAuthenticationFilter}, read by the transport context extractor. */
    public static final String ACTOR_ATTRIBUTE = "com.teammemory.mcp.security.ACTOR";

    /** Key under which the actor is stored in the {@code McpTransportContext} map. */
    public static final String ACTOR_KEY = "actor";

    private McpAuthContext() {
    }
}
