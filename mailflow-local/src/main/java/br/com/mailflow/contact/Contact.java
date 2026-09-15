package br.com.mailflow.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "contacts")
public class Contact {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @Column(length = 160)
    private String company;

    private LocalDate birthday;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContactStatus status = ContactStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Contact() {
    }

    public Contact(UUID workspaceId, String email, String displayName, String company, LocalDate birthday, String notes) {
        this.workspaceId = java.util.Objects.requireNonNull(workspaceId);
        update(email, displayName, company, birthday, notes);
    }

    public UUID getWorkspaceId() { return workspaceId; }

    public void update(String email, String displayName, String company, LocalDate birthday, String notes) {
        this.email = email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
        this.displayName = blankToNull(displayName);
        this.company = blankToNull(company);
        this.birthday = birthday;
        this.notes = blankToNull(notes);
    }

    public void changeStatus(ContactStatus status) {
        this.status = status;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @PrePersist
    void beforeInsert() {
        var now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCompany() {
        return company;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public ContactStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
