package br.com.mailflow.settings.smtp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SmtpAccountRepository extends JpaRepository<SmtpAccount, UUID> {

    List<SmtpAccount> findAllByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    java.util.Optional<SmtpAccount> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);

    boolean existsByWorkspaceIdAndNameIgnoreCaseAndIdNot(UUID workspaceId, String name, UUID id);
}
