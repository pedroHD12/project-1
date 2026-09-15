package br.com.mailflow.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RegistrationSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired AccountStore accounts;
    @Autowired PasswordEncoder encoder;

    @BeforeEach @AfterEach void cleanAccounts() { jdbc.sql("delete from app_users").update(); }

    RegistrationForm form(String email) {
        var form = new RegistrationForm(); form.setName("Teste"); form.setEmail(email);
        form.setPassword("phrase-for-local-test"); form.setConfirmPassword("phrase-for-local-test"); return form;
    }

    @Test void createsOneOwnerAndClosesRegistration() throws Exception {
        mvc.perform(post("/register").with(csrf()).param("name", "Teste").param("email", "Owner@Example.test")
                .param("password", "phrase-for-local-test").param("confirmPassword", "phrase-for-local-test"))
                .andExpect(redirectedUrl("/login"));
        assertThat(jdbc.sql("select normalized_email from app_users").query(String.class).single()).isEqualTo("owner@example.test");
        var hash = jdbc.sql("select password_hash from app_users").query(String.class).single();
        assertThat(hash).isNotEqualTo("phrase-for-local-test");
        assertThat(encoder.matches("phrase-for-local-test", hash)).isTrue();
        mvc.perform(get("/register")).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/register").with(csrf()).param("email", "attacker@example.test"))
                .andExpect(redirectedUrl("/login"));
        assertThat(jdbc.sql("select count(*) from app_users").query(Long.class).single()).isEqualTo(1);
    }

    @Test void concurrentClaimsOnlyCreateOneOwner() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> accounts.register(form("first@example.test")));
            var second = executor.submit(() -> accounts.register(form("second@example.test")));
            assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.sql("select count(*) from app_users").query(Long.class).single()).isEqualTo(1);
    }

    @Test void realLoginUsesPersistentAccountAndLogoutInvalidatesSession() throws Exception {
        accounts.register(form("owner@example.test"));
        var result = mvc.perform(post("/login").with(csrf()).param("username", " OWNER@EXAMPLE.TEST ")
                .param("password", "phrase-for-local-test")).andExpect(authenticated()).andExpect(redirectedUrl("/"))
                .andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/contacts").session(session)).andExpect(status().isOk());
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void fiveFailuresLockAccountAndSuccessfulLoginResetsAfterExpiry() {
        accounts.register(form("owner@example.test"));
        for (int n = 0; n < 5; n++) assertThat(accounts.authenticate("owner@example.test", "wrong")).isNull();
        assertThat(accounts.authenticate("owner@example.test", "phrase-for-local-test")).isNull();
        jdbc.sql("update app_users set locked_until = timestamp '2000-01-01 00:00:00'").update();
        assertThat(accounts.authenticate("owner@example.test", "phrase-for-local-test")).isNotNull();
        assertThat(jdbc.sql("select failed_attempts from app_users").query(Integer.class).single()).isZero();
    }

    @Test void neverEchoesPasswordOnInvalidRegistration() throws Exception {
        mvc.perform(post("/register").with(csrf()).param("name", "Teste").param("email", "bad-email")
                .param("password", "private-test-password").param("confirmPassword", "private-test-password"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("private-test-password"))));
    }

    @Test void rejectsUtf8PasswordsLongerThanBcryptLimit() {
        var f = form("owner@example.test"); f.setPassword("á".repeat(40)); f.setConfirmPassword(f.getPassword());
        assertThatThrownBy(() -> accounts.register(f)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> accounts.authenticate("owner@example.test", "a".repeat(100))).doesNotThrowAnyException();
    }

    @Test void loginHasPortugueseForm() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Entrar")));
    }

    @Test void firstOwnerCanOpenRegistration() throws Exception {
        mvc.perform(get("/register")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Criar minha conta")));
    }

    @Test void registrationRejectsWeakPassword() throws Exception {
        mvc.perform(post("/register").with(csrf()).param("name", "Owner")
                .param("email", "owner@example.test").param("password", "short")
                .param("confirmPassword", "short"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("12")));
    }

    @Test void registrationRequiresCsrf() throws Exception {
        mvc.perform(post("/register").param("email", "owner@example.test"))
                .andExpect(status().isForbidden());
    }
}
