package com.teammemory.mcp.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Living state for one shared memory. `path` uniqueness among active
 * (non-deleted) rows is enforced by a partial DB index — not by
 * {@code @Column(unique = true)} — since a soft-deleted path may be reused.
 */
@Entity
@Table(name = "memory_entry")
public class MemoryEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoryCategory category;

    @Column(nullable = false)
    private String content;

    /**
     * Backs JPA optimistic locking. A write against a stale version throws
     * {@link jakarta.persistence.OptimisticLockException}; the service layer
     * maps that to HTTP 409 so callers know to re-read and retry.
     */
    @Version
    private Long version;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected MemoryEntry() {
        // JPA
    }

    public MemoryEntry(String path, MemoryCategory category, String content, String actor) {
        this.path = path;
        this.category = category;
        this.content = content;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void updateContent(String content, String actor) {
        this.content = content;
        this.updatedBy = actor;
    }

    public void softDelete(String actor) {
        this.deletedAt = Instant.now();
        this.updatedBy = actor;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getId() {
        return id;
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

    public Long getVersion() {
        return version;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
