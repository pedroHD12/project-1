package br.com.mailflow.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.runtime.database-url=jdbc:postgresql://db.example.test/mailflow?sslmode=verify-full",
        "app.owner.email=owner@example.test",
        "app.owner.setup-token=initial-owner-token"
})
@AutoConfigureMockMvc
@ActiveProfiles("cloud")
class OwnerSetupControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @BeforeEach @AfterEach
    void cleanAccounts() {
        jdbc.sql("delete from app_users").update();
    }

    @Test
    void cloudSetupCreatesOnlyTheConfiguredOwnerOnce() throws Exception {
        mvc.perform(post("/setup").secure(true).with(csrf())
                        .param("setupToken", "initial-owner-token")
                        .param("name", "Owner")
                        .param("email", "owner@example.test")
                        .param("password", "phrase-for-cloud-test")
                        .param("confirmPassword", "phrase-for-cloud-test"))
                .andExpect(redirectedUrl("/login"));

        assertThat(jdbc.sql("select normalized_email from app_users").query(String.class).single())
                .isEqualTo("owner@example.test");
        mvc.perform(get("/setup").secure(true)).andExpect(status().isNotFound());
    }

    @Test
    void cloudSetupRejectsDifferentEmailAndPublicRegistration() throws Exception {
        mvc.perform(post("/setup").secure(true).with(csrf())
                        .param("setupToken", "initial-owner-token")
                        .param("name", "Attacker")
                        .param("email", "attacker@example.test")
                        .param("password", "phrase-for-cloud-test")
                        .param("confirmPassword", "phrase-for-cloud-test"))
                .andExpect(status().isOk());

        assertThat(jdbc.sql("select count(*) from app_users").query(Long.class).single()).isZero();
        mvc.perform(get("/register").secure(true)).andExpect(status().isNotFound());
    }
}
