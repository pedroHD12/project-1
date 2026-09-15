package br.com.mailflow.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_templates")
public class EmailTemplate {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 998)
    private String subject;

    @Column(name = "body_text", columnDefinition = "text")
    private String bodyText;

    @Column(name = "body_html", columnDefinition = "text")
    private String bodyHtml;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmailTemplate() {
    }

    public EmailTemplate(UUID workspaceId, String name, String subject, String bodyText, String bodyHtml) {
        this.workspaceId = java.util.Objects.requireNonNull(workspaceId);
        update(name, subject, bodyText, bodyHtml);
    }

    public UUID getWorkspaceId() { return workspaceId; }

    public void update(String name, String subject, String bodyText, String bodyHtml) {
        this.name = name.strip();
        this.subject = subject.strip();
        this.bodyText = blankToNull(bodyText);
        this.bodyHtml = blankToNull(bodyHtml);
    }

    public void archive() {
        active = false;
    }

    public void restore() {
        active = true;
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

    public String getName() {
        return name;
    }

    public String getSubject() {
        return subject;
    }

    public String getBodyText() {
        return bodyText;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
