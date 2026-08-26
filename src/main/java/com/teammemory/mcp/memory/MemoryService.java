package com.teammemory.mcp.memory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MemoryService {

    private final MemoryEntryRepository repository;
    private final EntityManager entityManager;

    public MemoryService(MemoryEntryRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<MemoryEntry> list(MemoryCategory category, String pathPrefix) {
        if (category != null) {
            return repository.findAllByCategoryAndDeletedAtIsNullOrderByPath(category);
        }
        if (pathPrefix != null && !pathPrefix.isBlank()) {
            return repository.findAllByPathStartingWithAndDeletedAtIsNullOrderByPath(pathPrefix);
        }
        return repository.findAllByDeletedAtIsNullOrderByPath();
    }

    @Transactional(readOnly = true)
    public MemoryEntry read(String path) {
        return repository.findByPathAndDeletedAtIsNull(path)
                .orElseThrow(() -> new MemoryNotFoundException(path));
    }

    /**
     * Create-or-update semantics: {@code expectedVersion} absent means "create
     * a new path" (fails if one already exists); present means "update, but
     * only if nobody else wrote first." The pre-check below fails fast on an
     * already-stale caller; the explicit {@link EntityManager#flush()} still
     * covers the narrow window where a concurrent write lands between that
     * check and this transaction's commit — {@code @Version} enforces it at
     * the SQL level (an {@code UPDATE ... WHERE version = ?} that matches zero
     * rows), which is what actually makes this safe, not the pre-check alone.
     */
    @Transactional
    public MemoryEntry write(String path, MemoryCategory category, String content, Long expectedVersion, String actor) {
        var existing = repository.findByPathAndDeletedAtIsNull(path);

        if (existing.isEmpty()) {
            if (expectedVersion != null) {
                throw new MemoryNotFoundException(path);
            }
            return repository.save(new MemoryEntry(path, category, content, actor));
        }

        MemoryEntry entry = existing.get();
        if (expectedVersion == null) {
            throw new MemoryConflictException(
                    "A memory already exists at '%s' (current version %d). Call memory_read first and pass its version as expectedVersion."
                            .formatted(path, entry.getVersion()));
        }
        if (!expectedVersion.equals(entry.getVersion())) {
            throw new MemoryConflictException(
                    "Version conflict at '%s': you expected version %d but the current version is %d. Someone else wrote first — call memory_read again and retry."
                            .formatted(path, expectedVersion, entry.getVersion()));
        }

        entry.updateContent(content, actor);
        repository.save(entry);
        try {
            entityManager.flush();
        } catch (OptimisticLockException e) {
            throw new MemoryConflictException(
                    "Version conflict at '%s': someone else wrote first. Call memory_read again and retry."
                            .formatted(path));
        }
        return entry;
    }
}
