package br.com.mailflow.settings.smtp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpAccountServiceTest {

    @Mock
    private SmtpAccountRepository repository;

    @Mock
    private SecretProtector secretProtector;

    private SmtpAccountService service;

    @BeforeEach
    void setUp() {
        service = new SmtpAccountService(repository, secretProtector, new br.com.mailflow.security.CurrentWorkspace() {
            @Override public java.util.UUID id() { return br.com.mailflow.security.AccountStore.INITIAL_WORKSPACE; }
        }, jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());
    }

    @Test
    void protectsSecretBeforeSavingAccount() {
        var form = new SmtpAccountForm();
        form.setName("Pessoal");
        form.setProvider("CUSTOM");
        form.setDefaultSender("pedro@example.com");
        form.setHost("smtp.example.com");
        form.setPort(587);
        form.setUsername("pedro@example.com");
        form.setPassword("segredo");
        form.setEncryptionMode(EncryptionMode.STARTTLS);
        form.setEnabled(true);

        when(secretProtector.protect("segredo")).thenReturn("dpapi:cifrado");
        when(repository.save(any(SmtpAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(form);

        var captor = ArgumentCaptor.forClass(SmtpAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSecretReference()).isEqualTo("dpapi:cifrado");
    }

    @Test void removingAuthenticationRemovesTheUnusedProtectedSecret() {
        var id = java.util.UUID.randomUUID();
        var account = new SmtpAccount(br.com.mailflow.security.AccountStore.INITIAL_WORKSPACE,
                "Pessoal", "smtp.example.test", 587, "owner@example.test", "dpapi:old", EncryptionMode.STARTTLS, "owner@example.test", true);
        when(repository.findByIdAndWorkspaceId(id, br.com.mailflow.security.AccountStore.INITIAL_WORKSPACE))
                .thenReturn(java.util.Optional.of(account));
        var form = SmtpAccountForm.from(account);
        form.setUsername("");
        service.update(id, form);
        assertThat(account.hasProtectedSecret()).isFalse();
    }

    @Test
    void reprotectsAnOldCloudCredentialAfterItIsRead() {
        var id = java.util.UUID.randomUUID();
        var account = new SmtpAccount(br.com.mailflow.security.AccountStore.INITIAL_WORKSPACE,
                "Pessoal", "smtp.example.test", 587, "owner@example.test", "aesgcm:v0:old", EncryptionMode.STARTTLS, "owner@example.test", true);
        when(repository.findByIdAndWorkspaceId(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(br.com.mailflow.security.AccountStore.INITIAL_WORKSPACE)))
                .thenReturn(java.util.Optional.of(account));
        when(secretProtector.unprotect("aesgcm:v0:old")).thenReturn("application-password");
        when(secretProtector.requiresReprotect("aesgcm:v0:old")).thenReturn(true);
        when(secretProtector.protect("application-password")).thenReturn("aesgcm:v1:new");

        assertThat(service.revealSecret(account)).isEqualTo("application-password");
        assertThat(account.getSecretReference()).isEqualTo("aesgcm:v1:new");
    }
}
