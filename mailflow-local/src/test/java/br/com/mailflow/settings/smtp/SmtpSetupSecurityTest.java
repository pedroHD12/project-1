package br.com.mailflow.settings.smtp;

import br.com.mailflow.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SmtpSetupSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired SmtpAccountRepository accounts;
    final AccountPrincipal owner = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), "owner@example.test");

    @Test void gmailNeedsOnlyEmailAndAppPasswordAndIgnoresForgedServer() throws Exception {
        mvc.perform(post("/settings/smtp").with(user(owner)).with(csrf())
            .param("defaultSender", "person@gmail.com").param("password", "synthetic-app-secret")
            .param("host", "evil.example.test").param("port", "25").param("encryptionMode", "NONE"))
            .andExpect(redirectedUrl("/settings/smtp"));
        var saved = accounts.findAllByWorkspaceIdOrderByNameAsc(owner.workspaceId()).getFirst();
        assertThat(saved.getHost()).isEqualTo("smtp.gmail.com");
        assertThat(saved.getPort()).isEqualTo(587);
        assertThat(saved.getEncryptionMode()).isEqualTo(EncryptionMode.STARTTLS);
        assertThat(saved.getUsername()).isEqualTo("person@gmail.com");
        assertThat(saved.getSecretReference()).doesNotContain("synthetic-app-secret");
    }
    @Test void forgedExistingSecretCannotSkipCredentials() throws Exception {
        mvc.perform(post("/settings/smtp").with(user(owner)).with(csrf())
            .param("name", "Forged").param("host", "smtp.example.test").param("provider", "CUSTOM")
            .param("defaultSender", "a@example.test").param("username", "a@example.test").param("existingSecret", "true"))
            .andExpect(status().isOk()).andExpect(model().attributeHasErrors("smtpAccountForm"));
        assertThat(accounts.findAllByWorkspaceIdOrderByNameAsc(owner.workspaceId())).isEmpty();
    }
    @Test void refusesUnencryptedCredentials() throws Exception {
        mvc.perform(post("/settings/smtp").with(user(owner)).with(csrf())
            .param("name", "Unsafe").param("provider", "CUSTOM").param("host", "smtp.example.test")
            .param("defaultSender", "a@example.test").param("username", "a@example.test")
            .param("password", "synthetic-private-value").param("encryptionMode", "NONE"))
            .andExpect(status().isOk()).andExpect(model().attributeHasErrors("smtpAccountForm"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-private-value"))));
        assertThat(accounts.findAllByWorkspaceIdOrderByNameAsc(owner.workspaceId())).isEmpty();
    }
    @Test void untrustedHostIsRejectedBeforeRegistration() throws Exception {
        mvc.perform(get("/register").header("Host", "attacker.example.test"))
            .andExpect(status().isForbidden());
    }
    @Test void sidebarHasExtrasLogoutAndNoInlineScripts() throws Exception {
        mvc.perform(get("/contacts").with(user(owner))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Mais recursos")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/logout\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("onsubmit="))));
    }
    @Test void nonLocalRequestIsRejectedEvenWithLocalHost() throws Exception {
        mvc.perform(get("/login").with(request -> { request.setRemoteAddr("203.0.113.5"); return request; }))
            .andExpect(status().isForbidden());
    }
}
