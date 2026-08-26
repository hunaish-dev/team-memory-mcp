package com.teammemory.mcp.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemoryVersionRepository extends JpaRepository<MemoryVersion, UUID> {

    List<MemoryVersion> findAllByMemoryIdOrderByCreatedAtDesc(UUID memoryId);
}
