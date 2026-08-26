package com.teammemory.mcp.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit snapshot. Rows are written exclusively by the
 * {@code record_memory_version} DB trigger (see V1__init_schema.sql), never
 * by application code — so the trail stays correct even if a caller writes
 * to memory_entry outside this app.
 */
@Entity
@Table(name = "memory_version")
public class MemoryVersion {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "memory_id", nullable = false)
    private UUID memoryId;

    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoryCategory category;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoryOperation operation;

    @Column(nullable = false)
    private String actor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MemoryVersion() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public UUID getMemoryId() {
        return memoryId;
    }

    public Long getVersion() {
        return version;
    }

    public String getPath() {
        return path;
    }

    public MemoryCategory getCategory() {
        return category;
    }

    public String getContent() {
        return content;
    }

    public MemoryOperation getOperation() {
        return operation;
    }

    public String getActor() {
        return actor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
