package com.teammemory.mcp.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemoryEntryRepository extends JpaRepository<MemoryEntry, UUID> {

    Optional<MemoryEntry> findByPathAndDeletedAtIsNull(String path);

    List<MemoryEntry> findAllByDeletedAtIsNullOrderByPath();

    List<MemoryEntry> findAllByCategoryAndDeletedAtIsNullOrderByPath(MemoryCategory category);

    List<MemoryEntry> findAllByPathStartingWithAndDeletedAtIsNullOrderByPath(String pathPrefix);
}
