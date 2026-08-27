package com.teammemory.mcp.mcp;

import com.teammemory.mcp.memory.MemoryCategory;
import com.teammemory.mcp.memory.MemoryEntry;
import com.teammemory.mcp.memory.MemoryService;
import com.teammemory.mcp.security.McpAuthContext;
import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the MCP-facing parameter handling (category parsing, DTO
 * mapping) with the service layer mocked. The concurrency/persistence
 * contract itself is covered by {@code MemoryServiceIT}, and the actual
 * MCP wire protocol (tool registration, error-result conversion) was
 * verified manually against a running server — not re-asserted here.
 */
@ExtendWith(MockitoExtension.class)
class MemoryToolsTest {

    @Mock
    MemoryService memoryService;

    MemoryTools tools;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        tools = new MemoryTools(memoryService);
    }

    /**
     * A never-persisted MemoryEntry has a null {@code @Version} field (Hibernate
     * only assigns it on first flush). Set it explicitly here since these tests
     * mock MemoryService and never go through real persistence.
     */
    private static MemoryEntry entry(String path, MemoryCategory category, String content, String actor) {
        MemoryEntry e = new MemoryEntry(path, category, content, actor);
        ReflectionTestUtils.setField(e, "version", 0L);
        return e;
    }

    @Test
    void memoryListParsesCategoryCaseInsensitively() {
        when(memoryService.list(eq(MemoryCategory.GOTCHA), isNull())).thenReturn(List.of());

        tools.memoryList("gotcha", null);

        verify(memoryService).list(MemoryCategory.GOTCHA, null);
    }

    @Test
    void memoryListTreatsBlankCategoryAsNoFilter() {
        when(memoryService.list(isNull(), eq("/decisions/"))).thenReturn(List.of());

        tools.memoryList("  ", "/decisions/");

        verify(memoryService).list(null, "/decisions/");
    }

    @Test
    void memoryListMapsEntriesToSummaries() {
        MemoryEntry e = entry("/decisions/a.md", MemoryCategory.DECISION, "content", "ali");
        when(memoryService.list(any(), any())).thenReturn(List.of(e));

        List<MemorySummary> result = tools.memoryList(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).path()).isEqualTo("/decisions/a.md");
        assertThat(result.get(0).category()).isEqualTo(MemoryCategory.DECISION);
        assertThat(result.get(0).updatedBy()).isEqualTo("ali");
    }

    @Test
    void memoryReadMapsEntryToContent() {
        MemoryEntry e = entry("/gotchas/x.md", MemoryCategory.GOTCHA, "watch out", "ali");
        when(memoryService.read("/gotchas/x.md")).thenReturn(e);

        MemoryContent result = tools.memoryRead("/gotchas/x.md");

        assertThat(result.path()).isEqualTo("/gotchas/x.md");
        assertThat(result.content()).isEqualTo("watch out");
        assertThat(result.version()).isEqualTo(e.getVersion());
    }

    private static McpTransportContext contextFor(String actor) {
        return McpTransportContext.create(Map.of(McpAuthContext.ACTOR_KEY, actor));
    }

    @Test
    void memoryWriteParsesCategoryAndDelegatesAllParameters() {
        ArgumentCaptor<MemoryCategory> categoryCaptor = ArgumentCaptor.forClass(MemoryCategory.class);
        MemoryEntry e = entry("/conventions/x.md", MemoryCategory.CONVENTION, "content", "ali");
        when(memoryService.write(eq("/conventions/x.md"), categoryCaptor.capture(), eq("content"), eq(5L), eq("ali")))
                .thenReturn(e);

        MemoryWriteResult result = tools.memoryWrite("/conventions/x.md", "convention", "content", 5L, contextFor("ali"));

        assertThat(categoryCaptor.getValue()).isEqualTo(MemoryCategory.CONVENTION);
        assertThat(result.path()).isEqualTo("/conventions/x.md");
        assertThat(result.version()).isEqualTo(e.getVersion());
    }

    @Test
    void memoryWriteAllowsNullExpectedVersionForCreate() {
        MemoryEntry e = entry("/glossary/x.md", MemoryCategory.GLOSSARY, "term", "ali");
        when(memoryService.write(eq("/glossary/x.md"), eq(MemoryCategory.GLOSSARY), eq("term"), isNull(), eq("ali")))
                .thenReturn(e);

        tools.memoryWrite("/glossary/x.md", "glossary", "term", null, contextFor("ali"));

        verify(memoryService).write("/glossary/x.md", MemoryCategory.GLOSSARY, "term", null, "ali");
    }

    @Test
    void memoryWriteRejectsAMissingActorRatherThanRecordingABlankOne() {
        assertThatThrownBy(() -> tools.memoryWrite("/x.md", "gotcha", "content", null, McpTransportContext.EMPTY))
                .isInstanceOf(IllegalStateException.class);
    }
}
