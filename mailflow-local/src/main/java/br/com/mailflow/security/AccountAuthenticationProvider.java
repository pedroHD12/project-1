package br.com.mailflow.security;

import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class AccountAuthenticationProvider implements AuthenticationProvider {
    private final AccountStore accounts;
    private long windowStarted = System.nanoTime();
    private int attempts;
    public AccountAuthenticationProvider(AccountStore accounts) { this.accounts = accounts; }
    @Override public synchronized Authentication authenticate(Authentication authentication) {
        if (!allowAttempt()) throw new BadCredentialsException("Tente novamente mais tarde.");
        var principal = accounts.authenticate(authentication.getName(),
                authentication.getCredentials() instanceof String password ? password : null);
        if (principal == null) {
            attempts++;
            throw new BadCredentialsException("E-mail ou senha inválidos.");
        }
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
    // Serialize local logins and bound failed hashing work, including nonexistent accounts.
    // Successful authentication must not exhaust the failure budget.
    private synchronized boolean allowAttempt() {
        var now = System.nanoTime();
        if (now - windowStarted >= 60_000_000_000L) { attempts = 0; windowStarted = now; }
        return attempts < 30;
    }
    @Override public boolean supports(Class<?> type) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type);
    }
}
