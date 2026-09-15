package br.com.mailflow;

import br.com.mailflow.contact.ContactRepository;
import br.com.mailflow.template.EmailTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MailFlowApplicationTest {
    private static final br.com.mailflow.security.AccountPrincipal OWNER = new br.com.mailflow.security.AccountPrincipal(
            java.util.UUID.randomUUID(), br.com.mailflow.security.AccountStore.INITIAL_WORKSPACE, "owner@example.test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private EmailTemplateRepository templateRepository;

    @BeforeEach
    void cleanDatabase() {
        contactRepository.deleteAll();
        templateRepository.deleteAll();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/contacts"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void rendersFirstIncrementPages() throws Exception {
        mockMvc.perform(get("/contacts").with(user(OWNER)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Contatos")));

        mockMvc.perform(get("/templates").with(user(OWNER)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Modelos de mensagem")));

        mockMvc.perform(get("/settings/smtp").with(user(OWNER)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Meu e-mail")));

        mockMvc.perform(get("/contacts/new").with(user(OWNER)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Salvar contato")));

        mockMvc.perform(get("/templates/new").with(user(OWNER)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Salvar modelo")));

        mockMvc.perform(get("/settings/smtp/new").with(user(OWNER)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Salvar e-mail")));
    }

    @Test
    void rejectsStateChangeWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/contacts")
                        .with(user(OWNER))
                        .param("email", "teste@example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsContactAndTemplateThroughWebForms() throws Exception {
        var owner = user(OWNER);

        mockMvc.perform(post("/contacts")
                        .with(owner)
                        .with(csrf())
                        .param("email", "PEDRO@Example.com")
                        .param("displayName", "Pedro"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts"));

        mockMvc.perform(post("/templates")
                        .with(owner)
                        .with(csrf())
                        .param("name", "Lembrete")
                        .param("subject", "Olá {{nome}}")
                        .param("bodyText", "Esta é uma mensagem para {{nome}}."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/templates"));

        assertThat(contactRepository.existsByWorkspaceIdAndEmailIgnoreCase(OWNER.workspaceId(), "pedro@example.com")).isTrue();
        assertThat(templateRepository.count()).isEqualTo(1);
    }
}
