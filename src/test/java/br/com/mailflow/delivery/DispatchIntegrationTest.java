package br.com.mailflow.delivery;

import br.com.mailflow.contact.*;
import br.com.mailflow.security.*;
import br.com.mailflow.settings.smtp.*;
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
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.delivery.enabled=false")
@AutoConfigureMockMvc
@DirtiesContext
class DispatchIntegrationTest {
    static final EmbeddedPostgres PG;
    static { try { PG = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start(); }
        catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void database(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
        props.add("spring.datasource.username", () -> "postgres"); props.add("spring.datasource.password", () -> "");
        props.add("spring.flyway.enabled", () -> "true"); props.add("spring.sql.init.mode", () -> "never");
        props.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    @Autowired JdbcClient jdbc;
    @Autowired DispatchService dispatch;
    @Autowired ContactService contacts;
    @Autowired SmtpAccountService accounts;
    @Autowired MockMvc mvc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean DispatchQueue queue;
    @Autowired DispatchWorker worker;
    @org.springframework.test.context.bean.override.mockito.MockitoBean EmailGateway gateway;
    AccountPrincipal owner;
    Contact contact;
    SmtpAccount account;

    @BeforeEach void setup() {
        jdbc.sql("delete from messages where new_flow").update();
        owner = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()+"@example.test");
        jdbc.sql("insert into workspaces(id,name) values (?, 'Synthetic')").param(owner.workspaceId()).update();
        asOwner();
        var cf = new ContactForm(); cf.setEmail("recipient@example.test"); cf.setDisplayName("Maria"); contact = contacts.create(cf);
        var sf = new SmtpAccountForm(); sf.setProvider("CUSTOM"); sf.setHost("smtp.example.test"); sf.setDefaultSender("sender@example.test"); account = accounts.create(sf);
    }
    void asOwner() { SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(owner,null,owner.getAuthorities())); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @AfterAll static void close() throws Exception { PG.close(); }
    DispatchForm form() {
        var form = new DispatchForm(); form.setAccountId(account.getId()); form.setContactIds(List.of(contact.getId()));
        form.setSubject("Olá {{nome}}"); form.setBodyText("Olá {{nome}}, teste privado."); return form;
    }
    long jobs(UUID id) { return jdbc.sql("select count(*) from delivery_jobs j join message_recipients r on r.id=j.message_recipient_id where r.message_id=?").param(id).query(Long.class).single(); }

    @Test void draftQueuesNothingAndConcurrentConfirmationQueuesOnlyOnce() throws Exception {
        var id = dispatch.createDraft(form()); assertThat(jobs(id)).isZero();
        try(var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Future<?>>();
            for(int i=0;i<4;i++) tasks.add(executor.submit(() -> { asOwner(); try { dispatch.confirm(id); } finally { clear(); } }));
            for(var task:tasks) task.get(15,TimeUnit.SECONDS);
        }
        assertThat(jobs(id)).isEqualTo(1);
        assertThat(dispatch.get(id).recipients().getFirst().subject()).isEqualTo("Olá Maria");
    }
    @Test void cannotUseForeignAccountContactOrMessage() {
        var id=dispatch.createDraft(form());
        owner = new AccountPrincipal(UUID.randomUUID(),UUID.randomUUID(),"other@example.test"); asOwner();
        assertThatThrownBy(() -> dispatch.get(id)).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        assertThatThrownBy(() -> dispatch.confirm(id)).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        assertThatThrownBy(() -> dispatch.createDraft(form())).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }
    @Test void blockedRecipientsCannotEnterDraft() {
        contacts.changeStatus(contact.getId(),ContactStatus.BLOCKED);
        assertThatThrownBy(() -> dispatch.createDraft(form())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void finiteRecurringScheduleIsPersistedAndCancellationIsIdempotent() {
        var form=form(); form.setMode("DAILY"); form.setOccurrences(3);
        form.setScheduledAt(java.time.LocalDateTime.now().plusDays(2).withSecond(0).withNano(0).toString());
        var id=dispatch.createDraft(form); dispatch.confirm(id); assertThat(jobs(id)).isEqualTo(3);
        dispatch.cancel(id); dispatch.cancel(id);
        assertThat(jdbc.sql("select count(*) from delivery_jobs j join message_recipients r on r.id=j.message_recipient_id where r.message_id=? and j.status='CANCELLED'").param(id).query(Long.class).single()).isEqualTo(3);
    }

    @Test void workerSendsOnePrivateEnvelopeAndPersistsResult() {
        var id=dispatch.createDraft(form()); dispatch.confirm(id);
        when(gateway.send(any(),any())).thenReturn(EmailGateway.Outcome.ACCEPTED);
        worker.processOne(); worker.processOne();
        assertThat(dispatch.get(id).jobs()).singleElement().satisfies(job -> { assertThat(job.status()).isEqualTo("SENT"); assertThat(job.attempts()).isEqualTo(1); });
        var envelope=org.mockito.ArgumentCaptor.forClass(EmailMessage.class);
        verify(gateway).send(any(),envelope.capture());
        assertThat(envelope.getValue().recipients()).containsExactly("recipient@example.test");
        assertThat(envelope.getValue().subject()).isEqualTo("Olá Maria");
    }
    @Test void ambiguousOutcomeIsNotRetriedOrManuallyReleased() {
        var id=dispatch.createDraft(form()); dispatch.confirm(id);
        when(gateway.send(any(),any())).thenReturn(EmailGateway.Outcome.UNKNOWN);
        worker.processOne(); worker.processOne();
        var job=dispatch.get(id).jobs().getFirst();
        assertThat(job.status()).isEqualTo("UNKNOWN"); assertThat(job.attempts()).isEqualTo(1);
        assertThatThrownBy(() -> dispatch.release(id,job.id())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void safeConnectionFailureRetriesOnlyThreeTimes() {
        var id=dispatch.createDraft(form()); dispatch.confirm(id);
        when(gateway.send(any(),any())).thenReturn(EmailGateway.Outcome.RETRYABLE);
        for(int i=0;i<4;i++) {
            jdbc.sql("update delivery_jobs set available_at=current_timestamp where status='RETRY'").update();
            worker.processOne();
        }
        var job=dispatch.get(id).jobs().getFirst();
        assertThat(job.status()).isEqualTo("FAILED"); assertThat(job.attempts()).isEqualTo(3);
    }
    @Test void futurePausedAndCancelledJobsDoNotSend() {
        var id=dispatch.createDraft(form()); dispatch.confirm(id); dispatch.pause(id,true);
        assertThat(queue.claim()).isEmpty(); dispatch.pause(id,false);
        jdbc.sql("update delivery_jobs set available_at=current_timestamp + interval '2 days'").update();
        assertThat(queue.claim()).isEmpty(); dispatch.cancel(id);
        assertThat(queue.claim()).isEmpty(); verifyNoInteractions(gateway);
    }
    @Test void contactBlockedAfterConfirmationIsSkipped() {
        var id=dispatch.createDraft(form()); dispatch.confirm(id);
        contacts.changeStatus(contact.getId(),ContactStatus.UNSUBSCRIBED); worker.processOne();
        assertThat(dispatch.get(id).jobs().getFirst().status()).isEqualTo("SKIPPED"); verifyNoInteractions(gateway);
    }
    @Test void lateJobIsDeliveredOnceAndMarkedAsLate() {
        var id=dispatch.createDraft(form()); dispatch.confirm(id);
        when(gateway.send(any(),any())).thenReturn(EmailGateway.Outcome.ACCEPTED);
        jdbc.sql("update delivery_jobs set available_at=current_timestamp - interval '1 hour'").update();
        worker.processOne(); assertThat(dispatch.get(id).jobs().getFirst().status()).isEqualTo("SENT_LATE");
        verify(gateway, times(1)).send(any(), any());
        worker.processOne();
        assertThat(dispatch.get(id).jobs().getFirst().status()).isEqualTo("SENT_LATE");
    }
    @Test void concurrentWorkersClaimSameJobOnlyOnce() throws Exception {
        var id=dispatch.createDraft(form()); dispatch.confirm(id);
        try(var executor=Executors.newFixedThreadPool(4)) {
            var results=executor.invokeAll(List.<Callable<Optional<DispatchQueue.Work>>>of(queue::claim,queue::claim,queue::claim,queue::claim));
            int claimed=0; for(var result:results) if(result.get().isPresent()) claimed++;
            assertThat(claimed).isEqualTo(1);
        }
    }
    @Test void selfTestIsIdempotentAndDoesNotQueueOriginalRecipients() {
        var id=dispatch.createDraft(form()); var test=dispatch.selfTest(id);
        assertThat(dispatch.selfTest(id)).isEqualTo(test); assertThat(jobs(id)).isZero();
        assertThat(dispatch.get(test).recipients()).singleElement().satisfies(r -> assertThat(r.email()).isEqualTo(owner.getUsername()));
    }
    @Test void browserRoutesRequireAuthenticationCsrfAndConfirmation() throws Exception {
        mvc.perform(get("/messages/new")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/messages/new").with(user(owner))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Revisar mensagem")));
        asOwner();
        var id=dispatch.createDraft(form());
        mvc.perform(get("/messages/"+id).with(user(owner))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("recipient@example.test")));
        mvc.perform(post("/messages/"+id+"/confirm").with(user(owner))).andExpect(status().isForbidden());
        assertThat(jobs(id)).isZero();
        mvc.perform(post("/messages/"+id+"/confirm").with(user(owner)).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(jobs(id)).isEqualTo(1);
        mvc.perform(get("/history").with(user(owner))).andExpect(status().isOk());
        mvc.perform(get("/schedules").with(user(owner))).andExpect(status().isOk());
        mvc.perform(get("/automations").with(user(owner))).andExpect(status().isOk());
    }
    @Test void invalidPostReturnsEditableFormAndCannotForgeWorkspace() throws Exception {
        mvc.perform(post("/messages").with(user(owner)).with(csrf()).param("subject","Hello").param("workspaceId",UUID.randomUUID().toString()))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("role=\"alert\"")));
        assertThat(jdbc.sql("select count(*) from messages where workspace_id=?").param(owner.workspaceId()).query(Long.class).single()).isZero();
    }
    @Test void pausingAfterClaimRetainsRecipientForResume() {
        var id=dispatch.createDraft(form()); dispatch.confirm(id);
        var first=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(invocation -> {
            var claimed=invocation.callRealMethod();
            if(first.getAndSet(false)) dispatch.pause(id,true);
            return claimed;
        }).when(queue).claim();
        worker.processOne();
        assertThat(dispatch.get(id).jobs().getFirst().status()).isEqualTo("PENDING");
        verifyNoInteractions(gateway);
        dispatch.pause(id,false); when(gateway.send(any(),any())).thenReturn(EmailGateway.Outcome.ACCEPTED);
        worker.processOne(); assertThat(dispatch.get(id).jobs().getFirst().status()).isEqualTo("SENT");
    }
    @Test void releasingOneJobCannotUnpauseSiblings() {
        var id=dispatch.createDraft(form()); dispatch.confirm(id);
        jdbc.sql("update delivery_jobs set status='MISSED'").update(); dispatch.pause(id,true);
        assertThatThrownBy(() -> dispatch.release(id,dispatch.get(id).jobs().getFirst().id())).isInstanceOf(IllegalArgumentException.class);
        assertThat(dispatch.get(id).message().status()).isEqualTo("PAUSED");
    }
    @Test void releasedReservationCannotFinishANewerClaim() {
        var id=dispatch.createDraft(form());dispatch.confirm(id);var old=queue.claim().orElseThrow();
        dispatch.pause(id,true);assertThat(queue.permitted(old)).isFalse();dispatch.pause(id,false);
        var current=queue.claim().orElseThrow();
        queue.finish(old,EmailGateway.Outcome.UNKNOWN,true);
        assertThat(dispatch.get(id).jobs().getFirst().status()).isEqualTo("PROCESSING");
        queue.finish(current,EmailGateway.Outcome.ACCEPTED,false);
        assertThat(dispatch.get(id).jobs().getFirst().status()).isEqualTo("SENT");
    }
    @Test void changedAccountAndCaseVariantSuppressionPreventSending() {
        var id=dispatch.createDraft(form()); dispatch.confirm(id);
        jdbc.sql("insert into blocked_recipients(id,workspace_id,email,reason) values (?,?,'RECIPIENT@EXAMPLE.TEST','MANUAL')").params(UUID.randomUUID(),owner.workspaceId()).update();
        worker.processOne(); assertThat(dispatch.get(id).jobs().getFirst().status()).isEqualTo("SKIPPED"); verifyNoInteractions(gateway);
        jdbc.sql("delete from blocked_recipients where workspace_id=?").param(owner.workspaceId()).update();
        var next=dispatch.createDraft(form()); dispatch.confirm(next);
        jdbc.sql("update smtp_accounts set host='changed.example.test' where id=?").param(account.getId()).update();
        worker.processOne(); assertThat(dispatch.get(next).jobs().getFirst().status()).isEqualTo("SKIPPED"); verifyNoInteractions(gateway);
    }
    @Test void lateFailureCannotRetryRecoveredUnknownJob() {
        var id=dispatch.createDraft(form()); dispatch.confirm(id); var work=queue.claim().orElseThrow();
        jdbc.sql("update delivery_jobs set locked_at=current_timestamp - interval '10 minutes'").update();queue.recoverInterrupted();
        queue.finish(work,EmailGateway.Outcome.RETRYABLE,false);
        assertThat(dispatch.get(id).jobs().getFirst().status()).isEqualTo("UNKNOWN"); assertThat(queue.claim()).isEmpty();
    }
    @Test void recoveredUnknownReservationCannotStartNetworkDelivery() {
        var id=dispatch.createDraft(form()); dispatch.confirm(id); var stale=queue.claim().orElseThrow();
        jdbc.sql("update delivery_jobs set locked_at=current_timestamp - interval '10 minutes'").update();
        queue.recoverInterrupted();

        assertThat(queue.permitted(stale)).isFalse();
        assertThat(dispatch.get(id).jobs().getFirst().status()).isEqualTo("UNKNOWN");
    }
    @Test void persistedMinuteAndDailyBudgetsSurviveWorkerInstances() {
        var id=dispatch.createDraft(form());dispatch.confirm(id);var job=dispatch.get(id).jobs().getFirst();
        jdbc.sql("insert into delivery_attempts(id,workspace_id,delivery_job_id,attempt_number,started_at,outcome) select gen_random_uuid(),?,?,n,current_timestamp,'PERMANENT_ERROR' from generate_series(1,12) n")
            .params(owner.workspaceId(),job.id()).update();
        assertThat(queue.claim()).isEmpty();
        jdbc.sql("update delivery_attempts set started_at=current_timestamp - interval '2 hours'").update();
        jdbc.sql("insert into delivery_attempts(id,workspace_id,delivery_job_id,attempt_number,started_at,outcome) select gen_random_uuid(),?,?,n,current_timestamp - interval '2 hours','PERMANENT_ERROR' from generate_series(13,200) n")
            .params(owner.workspaceId(),job.id()).update();
        assertThat(queue.claim()).isEmpty();
    }
}
