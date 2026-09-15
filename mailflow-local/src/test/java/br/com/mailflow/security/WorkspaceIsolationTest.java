package br.com.mailflow.security;

import br.com.mailflow.contact.*;
import br.com.mailflow.template.*;
import br.com.mailflow.settings.smtp.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
class WorkspaceIsolationTest {
    @Autowired ContactService contacts;
    @Autowired EmailTemplateService templates;
    @Autowired SmtpAccountService accounts;
    @Autowired MockMvc mvc;
    final AccountPrincipal a = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a@example.test");
    final AccountPrincipal b = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), "b@example.test");

    void as(AccountPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    Contact contact(String email) { var f = new ContactForm(); f.setEmail(email); return contacts.create(f); }
    EmailTemplate template() {
        var f = new EmailTemplateForm(); f.setName("Modelo privado"); f.setSubject("Assunto privado"); f.setBodyText("Mensagem privada");
        return templates.create(f);
    }
    SmtpAccount account(String name) {
        var f = new SmtpAccountForm(); f.setName(name); f.setHost("smtp.example.test");
        f.setProvider("CUSTOM"); f.setDefaultSender("owner@example.test"); return accounts.create(f);
    }
    @Test void listsOnlyCurrentWorkspaceContacts() {
        as(a); var own = contact("one@example.test");
        as(b); contact("two@example.test");
        as(a); assertThat(contacts.list(null)).extracting(Contact::getId).containsExactly(own.getId());
        assertThat(contacts.list("two")).isEmpty();
    }
    @Test void cannotReadOrDeleteForeignContact() {
        as(b); var other = contact("other@example.test");
        as(a); assertThatThrownBy(() -> contacts.get(other.getId())).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> contacts.delete(other.getId())).isInstanceOf(EntityNotFoundException.class);
    }
    @Test void identicalContactEmailIsAllowedInDifferentWorkspaces() {
        as(a); contact("same@example.test");
        as(b); assertThatCode(() -> contact("same@example.test")).doesNotThrowAnyException();
    }
    @Test void templateIsPrivate() {
        as(b); var other = template();
        as(a); assertThat(templates.list()).isEmpty();
        assertThatThrownBy(() -> templates.changeActive(other.getId(), false)).isInstanceOf(EntityNotFoundException.class);
    }
    @Test void sendingAccountIsPrivate() {
        as(b); var other = account("Pessoal");
        as(a); assertThat(accounts.list()).isEmpty();
        assertThatThrownBy(() -> accounts.get(other.getId())).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> accounts.revealSecret(other)).isInstanceOf(EntityNotFoundException.class);
        assertThatCode(() -> account("Pessoal")).doesNotThrowAnyException();
    }
    @Test void foreignIdsReturn404ForReadsAndMutations() throws Exception {
        as(b); var c = contact("private@example.test"); var t = template(); var s = account("Privada");
        SecurityContextHolder.clearContext();
        for (var path : new String[]{"/contacts/" + c.getId() + "/edit", "/templates/" + t.getId() + "/edit",
                "/settings/smtp/" + s.getId() + "/edit", "/settings/smtp/" + s.getId() + "/test-email"}) {
            mvc.perform(get(path).with(user(a))).andExpect(status().isNotFound());
        }
        mvc.perform(post("/contacts/" + c.getId() + "/delete").with(user(a)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/templates/" + t.getId() + "/active").param("active","false").with(user(a)).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(post("/settings/smtp/" + s.getId() + "/diagnose").with(user(a)).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(post("/settings/smtp/" + s.getId() + "/test-email").param("recipient","a@example.test").with(user(a)).with(csrf()))
            .andExpect(status().isNotFound());
    }
}
