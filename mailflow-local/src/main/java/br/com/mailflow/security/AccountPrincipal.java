package br.com.mailflow.security;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import java.util.UUID;

public final class AccountPrincipal extends User {
    private final UUID userId;
    private final UUID workspaceId;
    public AccountPrincipal(UUID userId, UUID workspaceId, String email) {
        super(email, "", AuthorityUtils.createAuthorityList("ROLE_OWNER"));
        this.userId = userId;
        this.workspaceId = workspaceId;
    }
    public UUID userId() { return userId; }
    public UUID workspaceId() { return workspaceId; }
}

