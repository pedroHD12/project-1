package br.com.mailflow.template;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {

    List<EmailTemplate> findTop100ByWorkspaceIdOrderByUpdatedAtDesc(UUID workspaceId);

    java.util.Optional<EmailTemplate> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    long countByWorkspaceId(UUID workspaceId);
}
