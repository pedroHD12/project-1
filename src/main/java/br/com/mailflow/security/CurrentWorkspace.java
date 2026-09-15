package br.com.mailflow.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class CurrentWorkspace {
    public AccountPrincipal principal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AccountPrincipal principal))
            throw new AccessDeniedException("Entre na sua conta para continuar.");
        return principal;
    }
    public UUID id() { return principal().workspaceId(); }
}

