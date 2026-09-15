package br.com.mailflow.settings.smtp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "smtp_accounts")
public class SmtpAccount {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(length = 320)
    private String username;

    @Column(name = "secret_reference", columnDefinition = "text")
    private String secretReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "encryption_mode", nullable = false, length = 20)
    private EncryptionMode encryptionMode;

    @Column(name = "default_sender", length = 320)
    private String defaultSender;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SmtpAccount() {
    }

    public SmtpAccount(
            UUID workspaceId,
            String name,
            String host,
            int port,
            String username,
            String secretReference,
            EncryptionMode encryptionMode,
            String defaultSender,
            boolean enabled
    ) {
        this.workspaceId = java.util.Objects.requireNonNull(workspaceId);
        this.secretReference = secretReference;
        update(name, host, port, username, encryptionMode, defaultSender, enabled);
    }

    public UUID getWorkspaceId() { return workspaceId; }

    public void update(
            String name,
            String host,
            int port,
            String username,
            EncryptionMode encryptionMode,
            String defaultSender,
            boolean enabled
    ) {
        this.name = name.strip();
        this.host = host.strip();
        this.port = port;
        this.username = blankToNull(username);
        this.encryptionMode = encryptionMode;
        this.defaultSender = blankToNull(defaultSender);
        this.enabled = enabled;
    }

    public void changeSecret(String secretReference) {
        this.secretReference = secretReference;
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

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    String getSecretReference() {
        return secretReference;
    }

    public boolean hasProtectedSecret() {
        return secretReference != null && !secretReference.isBlank();
    }

    public EncryptionMode getEncryptionMode() {
        return encryptionMode;
    }

    public String getDefaultSender() {
        return defaultSender;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
