package br.com.mailflow.security;

import br.com.mailflow.contact.*;
import br.com.mailflow.template.*;
import br.com.mailflow.settings.smtp.*;
import br.com.mailflow.dashboard.DashboardService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class PostgresFoundationTest {
    static final EmbeddedPostgres PG;
    static { try { PG = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start(); }
        catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void database(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
        props.add("spring.datasource.username", () -> "postgres");
        props.add("spring.datasource.password", () -> "");
        props.add("spring.flyway.enabled", () -> "true");
        props.add("spring.sql.init.mode", () -> "never");
        props.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired ContactService contacts;
    @Autowired EmailTemplateService templates;
    @Autowired SmtpAccountService accounts;
    @Autowired DashboardService dashboard;
    AccountPrincipal owner;

    @BeforeEach void workspace() {
        owner = createOwner();
        as(owner);
    }
    AccountPrincipal createOwner() {
        var principal = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()+"@example.test");
        jdbc.sql("insert into workspaces(id,name) values (?, 'Synthetic')").param(principal.workspaceId()).update();
        jdbc.sql("insert into app_users(id,workspace_id,name,email,normalized_email,password_hash) values (?,?,'Synthetic',?,?,?)")
            .params(principal.userId(), principal.workspaceId(), principal.getUsername(), principal.getUsername(), "unused-test-only-hash").update();
        return principal;
    }
    void as(AccountPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @AfterAll static void close() throws Exception { PG.close(); }

    @Test void concurrentContactDuplicatesDoNotReturnServerErrors() throws Exception { duplicateRequests("contacts"); }
    @Test void concurrentAccountDuplicatesDoNotReturnServerErrors() throws Exception { duplicateRequests("smtp_accounts"); }

    void duplicateRequests(String table) throws Exception {
        jdbc.sql("create or replace function test_insert_delay() returns trigger language plpgsql as $$ begin perform pg_sleep(0.3); return new; end $$").update();
        jdbc.sql("create trigger test_delay before insert on " + table + " for each row execute function test_insert_delay()").update();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var barrier = new CyclicBarrier(4);
            var jobs = new ArrayList<Future<Integer>>();
            for(int i=0;i<4;i++) jobs.add(executor.submit(() -> {
                barrier.await(5,TimeUnit.SECONDS);
                var request = table.equals("contacts") ? post("/contacts").param("email","duplicate@example.test")
                    : post("/settings/smtp").param("name","Duplicate").param("provider","CUSTOM")
                        .param("defaultSender","owner@example.test").param("host","smtp.example.test");
                return mvc.perform(request.with(user(owner)).with(csrf())).andReturn().getResponse().getStatus();
            }));
            var statuses = new ArrayList<Integer>();
            for(var job:jobs) statuses.add(job.get(15,TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(302,200,200,200);
            assertThat(jdbc.sql("select count(*) from " + table + " where workspace_id = ?")
                .param(owner.workspaceId()).query(Long.class).single()).isEqualTo(1);
        } finally { jdbc.sql("drop trigger test_delay on " + table).update(); }
    }

    @Test void persistedWorkspacesIsolateCountsAndAllUpdateRoutes() throws Exception {
        var other = createOwner(); as(other);
        var cf = new ContactForm(); cf.setEmail("private@example.test"); var contact = contacts.create(cf);
        var tf = new EmailTemplateForm(); tf.setName("Private"); tf.setSubject("Private"); tf.setBodyText("Private"); var template = templates.create(tf);
        var sf = new SmtpAccountForm(); sf.setName("Private"); sf.setProvider("CUSTOM"); sf.setHost("smtp.example.test"); sf.setDefaultSender("private@example.test"); var account = accounts.create(sf);
        as(owner);
        assertThat(dashboard.snapshot().contacts()).isZero();
        assertThat(dashboard.snapshot().templates()).isZero();
        assertThat(contacts.list(null)).isEmpty(); assertThat(templates.list()).isEmpty(); assertThat(accounts.list()).isEmpty();
        mvc.perform(post("/contacts/"+contact.getId()).with(user(owner)).with(csrf()).param("email","changed@example.test")).andExpect(status().isNotFound());
        mvc.perform(post("/templates/"+template.getId()).with(user(owner)).with(csrf()).param("name","Changed").param("subject","Changed").param("bodyText","Changed")).andExpect(status().isNotFound());
        mvc.perform(post("/settings/smtp/"+account.getId()).with(user(owner)).with(csrf()).param("name","Changed")).andExpect(status().isNotFound());
        as(other); assertThat(contacts.get(contact.getId()).getEmail()).isEqualTo("private@example.test");
        assertThat(templates.get(template.getId()).getName()).isEqualTo("Private"); assertThat(accounts.get(account.getId()).getName()).isEqualTo("Private");
    }
}
