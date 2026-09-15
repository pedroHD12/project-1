package br.com.mailflow.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationBudgetTest {
    @Test void successfulLoginsDoNotExhaustTheFailureBudget() {
        var accounts = mock(AccountStore.class);
        var owner = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), "owner@example.test");
        when(accounts.authenticate("owner@example.test", "synthetic")).thenReturn(owner);
        var provider = new AccountAuthenticationProvider(accounts);
        for (int i = 0; i < 35; i++) {
            var result = provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("owner@example.test", "synthetic"));
            assertThat(result.isAuthenticated()).isTrue();
            assertThat(result.getCredentials()).isNull();
        }
    }
    @Test void repeatedUnknownAccountFailuresStillHaveABoundedBudget() {
        var accounts = mock(AccountStore.class);
        var provider = new AccountAuthenticationProvider(accounts);
        for (int i = 0; i < 35; i++) {
            assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("missing@example.test", "synthetic")))
                .isInstanceOf(BadCredentialsException.class);
        }
        verify(accounts, times(30)).authenticate("missing@example.test", "synthetic");
    }
}
