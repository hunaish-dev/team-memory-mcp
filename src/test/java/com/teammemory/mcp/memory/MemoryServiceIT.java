package com.teammemory.mcp.memory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests against a real Postgres (Testcontainers) — required
 * because the correctness story here (partial unique index, @Version
 * translating to a real SQL-level optimistic-lock check, the audit trigger)
 * cannot be meaningfully verified against an in-memory substitute.
 */
@SpringBootTest
@Testcontainers
class MemoryServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MemoryService memoryService;

    @Autowired
    MemoryEntryRepository entryRepository;

    @Autowired
    MemoryVersionRepository versionRepository;

    /** Each test gets its own path namespace so tests never interfere with each other. */
    private static String path() {
        return "/test/" + UUID.randomUUID() + ".md";
    }

    @Test
    void createsNewMemoryWithVersionZero() {
        MemoryEntry entry = memoryService.write(path(), MemoryCategory.DECISION, "hello", null, "ali");

        assertThat(entry.getVersion()).isZero();
        assertThat(entry.getContent()).isEqualTo("hello");
        assertThat(entry.getCreatedBy()).isEqualTo("ali");
    }

    @Test
    void creatingAtAnExistingPathWithoutExpectedVersionConflicts() {
        String path = path();
        memoryService.write(path, MemoryCategory.DECISION, "first", null, "ali");

        assertThatThrownBy(() -> memoryService.write(path, MemoryCategory.DECISION, "second", null, "bob"))
                .isInstanceOf(MemoryConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void readReturnsCurrentContent() {
        String path = path();
        memoryService.write(path, MemoryCategory.GOTCHA, "watch out for X", null, "ali");

        MemoryEntry read = memoryService.read(path);

        assertThat(read.getContent()).isEqualTo("watch out for X");
        assertThat(read.getCategory()).isEqualTo(MemoryCategory.GOTCHA);
    }

    @Test
    void readingAMissingPathThrowsNotFound() {
        assertThatThrownBy(() -> memoryService.read(path()))
                .isInstanceOf(MemoryNotFoundException.class);
    }

    @Test
    void updatingWithCorrectExpectedVersionSucceedsAndIncrementsVersion() {
        String path = path();
        MemoryEntry created = memoryService.write(path, MemoryCategory.CONVENTION, "v0", null, "ali");

        MemoryEntry updated = memoryService.write(path, MemoryCategory.CONVENTION, "v1", created.getVersion(), "bob");

        assertThat(updated.getVersion()).isEqualTo(created.getVersion() + 1);
        assertThat(updated.getContent()).isEqualTo("v1");
        assertThat(updated.getUpdatedBy()).isEqualTo("bob");
    }

    @Test
    void updatingWithStaleExpectedVersionConflictsAndWritesNothing() {
        String path = path();
        MemoryEntry created = memoryService.write(path, MemoryCategory.CONVENTION, "v0", null, "ali");

        assertThatThrownBy(() -> memoryService.write(path, MemoryCategory.CONVENTION, "v1", 99L, "bob"))
                .isInstanceOf(MemoryConflictException.class)
                .hasMessageContaining("Version conflict");

        // the rejected write must not have touched the row or produced an audit version
        MemoryEntry unchanged = memoryService.read(path);
        assertThat(unchanged.getContent()).isEqualTo("v0");
        assertThat(versionRepository.findAllByMemoryIdOrderByCreatedAtDesc(created.getId())).hasSize(1);
    }

    @Test
    void updatingANonexistentPathWithExpectedVersionThrowsNotFound() {
        assertThatThrownBy(() -> memoryService.write(path(), MemoryCategory.CONVENTION, "x", 0L, "ali"))
                .isInstanceOf(MemoryNotFoundException.class);
    }

    @Test
    void listFiltersByCategory() {
        String decisionPath = path();
        String gotchaPath = path();
        memoryService.write(decisionPath, MemoryCategory.DECISION, "d", null, "ali");
        memoryService.write(gotchaPath, MemoryCategory.GOTCHA, "g", null, "ali");

        List<MemoryEntry> decisions = memoryService.list(MemoryCategory.DECISION, null);

        assertThat(decisions).extracting(MemoryEntry::getPath).contains(decisionPath).doesNotContain(gotchaPath);
    }

    @Test
    void listFiltersByPathPrefix() {
        String prefix = "/prefix-" + UUID.randomUUID() + "/";
        String matching = prefix + "a.md";
        String nonMatching = path();
        memoryService.write(matching, MemoryCategory.GLOSSARY, "a", null, "ali");
        memoryService.write(nonMatching, MemoryCategory.GLOSSARY, "b", null, "ali");

        List<MemoryEntry> results = memoryService.list(null, prefix);

        assertThat(results).extracting(MemoryEntry::getPath).containsExactly(matching);
    }

    @Test
    void writeRecordsAnAuditTrailInOrder() {
        String path = path();
        MemoryEntry created = memoryService.write(path, MemoryCategory.DECISION, "v0", null, "ali");
        memoryService.write(path, MemoryCategory.DECISION, "v1", created.getVersion(), "bob");

        List<MemoryVersion> versions = versionRepository.findAllByMemoryIdOrderByCreatedAtDesc(created.getId());

        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getOperation()).isEqualTo(MemoryOperation.MODIFIED);
        assertThat(versions.get(0).getActor()).isEqualTo("bob");
        assertThat(versions.get(1).getOperation()).isEqualTo(MemoryOperation.CREATED);
        assertThat(versions.get(1).getActor()).isEqualTo("ali");
    }

    @Test
    void softDeleteHidesFromReadAndFreesThePathForReuse() {
        String path = path();
        MemoryEntry entry = memoryService.write(path, MemoryCategory.GOTCHA, "temporary", null, "ali");

        entry.softDelete("ali");
        entryRepository.save(entry);

        assertThatThrownBy(() -> memoryService.read(path)).isInstanceOf(MemoryNotFoundException.class);

        MemoryEntry recreated = memoryService.write(path, MemoryCategory.GOTCHA, "reused path", null, "bob");
        assertThat(recreated.getContent()).isEqualTo("reused path");

        List<MemoryVersion> versions = versionRepository.findAllByMemoryIdOrderByCreatedAtDesc(entry.getId());
        assertThat(versions).extracting(MemoryVersion::getOperation).contains(MemoryOperation.DELETED);
    }

    @Test
    void concurrentConflictingWritesAllowExactlyOneSuccess() throws Exception {
        String path = path();
        MemoryEntry created = memoryService.write(path, MemoryCategory.DECISION, "start", null, "ali");
        long startingVersion = created.getVersion();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Callable<Boolean> racer = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
                memoryService.write(path, MemoryCategory.DECISION, "raced write", startingVersion, "racer");
                return true;
            } catch (MemoryConflictException e) {
                return false;
            }
        };

        try {
            List<Future<Boolean>> futures = executor.invokeAll(List.of(racer, racer));
            long successCount = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    successCount++;
                }
            }

            assertThat(successCount).isEqualTo(1);
            assertThat(memoryService.read(path).getVersion()).isEqualTo(startingVersion + 1);
        } finally {
            executor.shutdown();
        }
    }
}
