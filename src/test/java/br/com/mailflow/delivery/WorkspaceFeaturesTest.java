package br.com.mailflow.delivery;

import br.com.mailflow.security.AccountPrincipal;
import br.com.mailflow.inbox.*;
import br.com.mailflow.settings.smtp.*;
import br.com.mailflow.contact.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.delivery.enabled=false","app.inbox.enabled=false"})
@AutoConfigureMockMvc
@DirtiesContext
class WorkspaceFeaturesTest {
    static final EmbeddedPostgres PG;
    static { try { PG=EmbeddedPostgres.builder().setServerConfig("listen_addresses","127.0.0.1").start(); }
        catch(java.io.IOException ex) { throw new ExceptionInInitializerError(ex); } }
    @DynamicPropertySource static void database(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url",()->PG.getJdbcUrl("postgres","postgres"));
        props.add("spring.datasource.username",()->"postgres"); props.add("spring.datasource.password",()->"");
        props.add("spring.flyway.enabled",()->"true"); props.add("spring.sql.init.mode",()->"never");
        props.add("spring.jpa.hibernate.ddl-auto",()->"validate");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired InboxService inbox;
    @Autowired SmtpAccountService accounts;
    @Autowired ContactService contacts;
    @Autowired DispatchService dispatch;
    @org.springframework.test.context.bean.override.mockito.MockitoBean ImapReplyReader reader;
    @org.springframework.test.context.bean.override.mockito.MockitoBean SecretProtector protector;
    AccountPrincipal owner;
    @BeforeEach void owner() {
        owner=new AccountPrincipal(UUID.randomUUID(),UUID.randomUUID(),"owner@example.test");
        jdbc.sql("insert into workspaces(id,name) values (?, 'Synthetic')").param(owner.workspaceId()).update();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(owner,null,owner.getAuthorities()));
        when(protector.protect(anyString())).thenReturn("synthetic-protected");
        when(protector.unprotect(anyString())).thenReturn("synthetic-secret");
    }
    @AfterEach void clear() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    @AfterAll static void close() throws Exception { PG.close(); }

    @Test void saveIncompleteDraftCreatesNoDeliveryAndRemainsEditable() throws Exception {
        mvc.perform(post("/messages").with(user(owner)).with(csrf()).param("action","save")
            .param("bodyText","Ainda estou escrevendo"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/drafts"));
        var id=jdbc.sql("select id from saved_drafts where workspace_id=?").param(owner.workspaceId()).query(UUID.class).single();
        assertThat(jdbc.sql("select count(*) from delivery_jobs where workspace_id=?").param(owner.workspaceId()).query(Long.class).single()).isZero();
        mvc.perform(get("/drafts/"+id+"/edit").with(user(owner))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Ainda estou escrevendo")));
        mvc.perform(get("/drafts").with(user(owner))).andExpect(status().isOk());
    }

    @Test void draftUpdatesRejectStaleVersionsAndDeleteIsManualAndScoped() throws Exception {
        mvc.perform(post("/messages").with(user(owner)).with(csrf()).param("action","save").param("subject","Original"))
            .andExpect(status().is3xxRedirection());
        var id=jdbc.sql("select id from saved_drafts where workspace_id=?").param(owner.workspaceId()).query(UUID.class).single();
        mvc.perform(post("/drafts/"+id).with(user(owner)).with(csrf()).param("action","save").param("version","0").param("subject","Atualizado"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(post("/drafts/"+id).with(user(owner)).with(csrf()).param("action","save").param("version","0").param("subject","Desatualizado"))
            .andExpect(status().isOk());
        assertThat(jdbc.sql("select subject from saved_drafts where id=?").param(id).query(String.class).single()).isEqualTo("Atualizado");
        var stranger=new AccountPrincipal(UUID.randomUUID(),UUID.randomUUID(),"other@example.test");
        mvc.perform(get("/drafts/"+id+"/edit").with(user(stranger))).andExpect(status().isNotFound());
        mvc.perform(post("/drafts/"+id+"/delete").with(user(stranger)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/drafts/"+id+"/delete").with(user(owner))).andExpect(status().isForbidden());
        assertThat(jdbc.sql("select count(*) from saved_drafts where id=?").param(id).query(Long.class).single()).isOne();
        mvc.perform(post("/drafts/"+id+"/delete").with(user(owner)).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(jdbc.sql("select count(*) from saved_drafts where id=?").param(id).query(Long.class).single()).isZero();
    }

    @Test void editingDraftRetainsSelectedContactBeyondFirstHundredWithoutDuplicatesOrOtherWorkspace() throws Exception {
        jdbc.sql("""
            insert into contacts(id,workspace_id,email,display_name,status,created_at,updated_at)
            select gen_random_uuid(),?, 'contact'||n||'@example.test', 'Contact '||lpad(n::text,3,'0'),'ACTIVE',now(),now()
            from generate_series(1,101) n
            """).param(owner.workspaceId()).update();
        var first=jdbc.sql("select id from contacts where workspace_id=? and email='contact1@example.test'").param(owner.workspaceId()).query(UUID.class).single();
        var selected=jdbc.sql("select id from contacts where workspace_id=? and email='contact101@example.test'").param(owner.workspaceId()).query(UUID.class).single();
        var otherWorkspace=UUID.randomUUID(); var otherContact=UUID.randomUUID();
        jdbc.sql("insert into workspaces(id,name) values (?, 'Other')").param(otherWorkspace).update();
        jdbc.sql("insert into contacts(id,workspace_id,email,status,created_at,updated_at) values (?,?,'private@example.test','ACTIVE',now(),now())")
            .params(otherContact,otherWorkspace).update();
        mvc.perform(post("/messages").with(user(owner)).with(csrf()).param("action","save")
            .param("contactIds",first.toString(),selected.toString())).andExpect(status().is3xxRedirection());
        var id=jdbc.sql("select id from saved_drafts where workspace_id=?").param(owner.workspaceId()).query(UUID.class).single();
        var response=mvc.perform(get("/drafts/"+id+"/edit").with(user(owner))).andExpect(status().isOk()).andReturn();
        var document=org.jsoup.Jsoup.parse(response.getResponse().getContentAsString());
        var checked=document.select("input[name=contactIds][checked]").eachAttr("value");
        assertThat(checked).containsExactlyInAnyOrder(first.toString(),selected.toString());
        assertThat(document.select("input[name=contactIds]").eachAttr("value")).doesNotHaveDuplicates().doesNotContain(otherContact.toString());
        mvc.perform(post("/drafts/"+id).with(user(owner)).with(csrf()).param("action","save").param("version","0")
            .param("contactIds",checked.toArray(String[]::new))).andExpect(status().is3xxRedirection());
        assertThat(jdbc.sql("select contact_ids from saved_drafts where id=?").param(id).query(String.class).single().split(","))
            .containsExactlyInAnyOrder(first.toString(),selected.toString());
        // A forged selection must also be excluded when an invalid form is rendered again.
        var invalid=mvc.perform(post("/drafts/"+id).with(user(owner)).with(csrf()).param("action","unexpected").param("version","1")
            .param("contactIds",selected.toString(),otherContact.toString())).andExpect(status().isOk()).andReturn();
        assertThat(org.jsoup.Jsoup.parse(invalid.getResponse().getContentAsString()).select("input[name=contactIds]").eachAttr("value"))
            .contains(selected.toString()).doesNotContain(otherContact.toString());
    }

    @Test void unsupportedDraftActionCannotOverwriteSavedContent() throws Exception {
        mvc.perform(post("/messages").with(user(owner)).with(csrf()).param("action","save").param("subject","Original"));
        var id=jdbc.sql("select id from saved_drafts where workspace_id=?").param(owner.workspaceId()).query(UUID.class).single();
        mvc.perform(post("/drafts/"+id).with(user(owner)).with(csrf()).param("action","unexpected").param("version","0").param("subject","Changed"))
            .andExpect(status().isOk());
        assertThat(jdbc.sql("select subject from saved_drafts where id=?").param(id).query(String.class).single()).isEqualTo("Original");
        assertThat(jdbc.sql("select version from saved_drafts where id=?").param(id).query(Long.class).single()).isZero();
    }

    @Test void draftCannotSelectAnotherWorkspacesAccountAndMarkupIsEscaped() throws Exception {
        var other=UUID.randomUUID(); jdbc.sql("insert into workspaces(id,name) values (?, 'Other')").param(other).update();
        var account=UUID.randomUUID();
        jdbc.sql("insert into smtp_accounts(id,workspace_id,name,host,port,encryption_mode,enabled) values (?,?, 'Other','smtp.gmail.com',587,'STARTTLS',true)").params(account,other).update();
        mvc.perform(post("/messages").with(user(owner)).with(csrf()).param("action","save").param("accountId",account.toString()))
            .andExpect(status().isNotFound());
        mvc.perform(post("/messages").with(user(owner)).with(csrf()).param("action","save").param("subject","<script>bad()</script>").param("bodyHtml","</textarea><script>bad()</script>"))
            .andExpect(status().is3xxRedirection());
        var id=jdbc.sql("select id from saved_drafts where workspace_id=?").param(owner.workspaceId()).query(UUID.class).single();
        mvc.perform(get("/drafts/"+id+"/edit").with(user(owner))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;/textarea&gt;&lt;script&gt;bad()&lt;/script&gt;")));
    }

    private SmtpAccount gmail() {
        var form=new SmtpAccountForm(); form.setDefaultSender("sender@gmail.com"); form.setPassword("synthetic-secret"); return accounts.create(form);
    }
    private UUID outgoing(SmtpAccount account) {
        var cf=new ContactForm(); cf.setEmail("recipient@example.test"); var contact=contacts.create(cf);
        var form=new DispatchForm(); form.setAccountId(account.getId()); form.setContactIds(java.util.List.of(contact.getId())); form.setSubject("Original"); form.setBodyText("Synthetic outgoing");
        var id=dispatch.createDraft(form); dispatch.confirm(id); var job=dispatch.get(id).jobs().getFirst().id();
        jdbc.sql("update delivery_jobs set attempt_count=1,status='SENT' where id=?").param(job).update(); return job;
    }
    @Test void savedDraftRemainsAfterReviewAndConfirmation() throws Exception {
        var account=gmail(); var cf=new ContactForm(); cf.setEmail("recipient@example.test"); var contact=contacts.create(cf);
        mvc.perform(post("/messages").with(user(owner)).with(csrf()).param("action","save").param("accountId",account.getId().toString())
            .param("contactIds",contact.getId().toString()).param("subject","Guardado").param("bodyText","Minha mensagem"));
        var id=jdbc.sql("select id from saved_drafts where workspace_id=?").param(owner.workspaceId()).query(UUID.class).single();
        var response=mvc.perform(post("/drafts/"+id).with(user(owner)).with(csrf()).param("version","0").param("action","review")
            .param("accountId",account.getId().toString()).param("contactIds",contact.getId().toString()).param("subject","Guardado").param("bodyText","Minha mensagem"))
            .andExpect(status().is3xxRedirection()).andReturn();
        var messageId=UUID.fromString(response.getResponse().getRedirectedUrl().substring("/messages/".length()));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(owner,null,owner.getAuthorities()));
        dispatch.confirm(messageId);
        assertThat(jdbc.sql("select count(*) from saved_drafts where id=?").param(id).query(Long.class).single()).isOne();
    }
    @Test void inboxRequiresOptInAndCorrelatesAccountSenderAndMessageIdsWithoutDuplicates() throws Exception {
        var account=gmail(); var job=outgoing(account);
        assertThat(inbox.sync(account.getId(),owner.workspaceId()).status()).isEqualTo("DISABLED");
        verifyNoInteractions(reader);
        inbox.configure(account.getId(),true);
        var header=new ReplyHeader(100,"recipient@example.test","Re: Original",java.time.Instant.now(),java.util.List.of(job),"<reply@example.test>");
        var unrelated=new ReplyHeader(101,"other@example.test","Spoof",java.time.Instant.now(),java.util.List.of(job),"<other@example.test>");
        when(reader.read(any(),anyString(),anyLong(),anyLong(),any())).thenReturn(new ImapReplyReader.Batch(71,101,java.util.List.of(
            new ImapReplyReader.Reply(header,"<script>hostile()</script>"),new ImapReplyReader.Reply(unrelated,"Unrelated"))));
        assertThat(inbox.sync(account.getId(),owner.workspaceId()).imported()).isOne();
        assertThat(inbox.sync(account.getId(),owner.workspaceId()).imported()).isZero();
        assertThat(inbox.list(0)).singleElement().satisfies(reply->assertThat(reply.from()).isEqualTo("recipient@example.test"));
        mvc.perform(get("/inbox").with(user(owner))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;hostile()&lt;/script&gt;")));
        var stranger=new AccountPrincipal(UUID.randomUUID(),UUID.randomUUID(),"other@example.test");
        mvc.perform(post("/inbox/accounts/"+account.getId()).with(user(stranger)).with(csrf()).param("enabled","true")).andExpect(status().isNotFound());
        mvc.perform(post("/inbox/accounts/"+account.getId()).with(user(owner)).param("enabled","false")).andExpect(status().isForbidden());
    }
    @Test void changedIdentityDoesNotReuseMailboxAuthorization() throws Exception {
        var account=gmail(); inbox.configure(account.getId(),true);
        jdbc.sql("update smtp_accounts set default_sender='other@gmail.com',username='other@gmail.com' where id=?").param(account.getId()).update();
        assertThat(inbox.sync(account.getId(),owner.workspaceId()).status()).isEqualTo("DISABLED"); verifyNoInteractions(reader);
    }
    @Test void simultaneousRefreshIsBusyAndUnknownOrAnotherAccountsReferenceIsNotImported() throws Exception {
        var account=gmail(); var job=outgoing(account);
        var form=new SmtpAccountForm(); form.setDefaultSender("second@gmail.com"); form.setPassword("synthetic-secret");
        var second=accounts.create(form); inbox.configure(second.getId(),true);
        doAnswer(call->{
            assertThat(inbox.sync(second.getId(),owner.workspaceId()).status()).isEqualTo("BUSY");
            return new ImapReplyReader.Batch(71,101,java.util.List.of(
                new ImapReplyReader.Reply(new ReplyHeader(100,"recipient@example.test","Wrong account",java.time.Instant.now(),java.util.List.of(job),"<wrong@example.test>"),"Wrong account"),
                new ImapReplyReader.Reply(new ReplyHeader(101,"recipient@example.test","Unknown",java.time.Instant.now(),java.util.List.of(UUID.randomUUID()),"<unknown@example.test>"),"Unknown")));
        }).when(reader).read(any(),anyString(),anyLong(),anyLong(),any());
        assertThat(inbox.sync(second.getId(),owner.workspaceId()).imported()).isZero();
        verify(reader,times(1)).read(any(),anyString(),anyLong(),anyLong(),any());
        assertThat(inbox.list(0)).isEmpty();
    }
    @Test void incomingNulCannotBlockTheCursorOrFollowingReplies() throws Exception {
        var account=gmail(); var job=outgoing(account); inbox.configure(account.getId(),true);
        when(reader.read(any(),anyString(),anyLong(),anyLong(),any())).thenReturn(new ImapReplyReader.Batch(71,101,java.util.List.of(
            new ImapReplyReader.Reply(new ReplyHeader(100,"recipient@example.test","NUL",java.time.Instant.now(),java.util.List.of(job),"<nul@example.test>"),"Before\0After"),
            new ImapReplyReader.Reply(new ReplyHeader(101,"recipient@example.test","Next",java.time.Instant.now(),java.util.List.of(job),"<next@example.test>"),"Following reply"))));
        assertThat(inbox.sync(account.getId(),owner.workspaceId()).imported()).isEqualTo(2);
        assertThat(jdbc.sql("select last_uid from mailbox_connections where account_id=?").param(account.getId()).query(Long.class).single()).isEqualTo(101);
        assertThat(inbox.list(0)).hasSize(2).allSatisfy(reply->assertThat(reply.text()).doesNotContain("\0"));
    }
    @Test void quotaPersistsAvailableCapacityAndResumesAfterDeletion() throws Exception {
        var account=gmail(); var job=outgoing(account); inbox.configure(account.getId(),true);
        var messageId=jdbc.sql("select message_id from message_recipients r join delivery_jobs j on j.message_recipient_id=r.id where j.id=?")
            .param(job).query(UUID.class).single();
        jdbc.sql("""
            insert into received_replies(id,workspace_id,account_id,message_id,incoming_key,from_email,subject,body_text,received_at)
            select gen_random_uuid(),?,?,?, 'existing-'||n,'recipient@example.test','Existing','Existing',now()
            from generate_series(1,4999) n
            """).params(owner.workspaceId(),account.getId(),messageId).update();
        var first=new ImapReplyReader.Reply(new ReplyHeader(100,"recipient@example.test","First",java.time.Instant.now(),java.util.List.of(job),"<first@example.test>"),"First reply");
        var next=new ImapReplyReader.Reply(new ReplyHeader(101,"recipient@example.test","Next",java.time.Instant.now(),java.util.List.of(job),"<next@example.test>"),"Next reply");
        when(reader.read(any(),anyString(),eq(0L),eq(0L),any())).thenReturn(new ImapReplyReader.Batch(71,105,java.util.List.of(first,next)));
        when(reader.read(any(),anyString(),eq(71L),eq(100L),any())).thenReturn(new ImapReplyReader.Batch(71,105,java.util.List.of(next)));

        var result=inbox.sync(account.getId(),owner.workspaceId());
        assertThat(result.status()).isEqualTo("UPDATED");
        assertThat(result.imported()).isOne();
        assertThat(jdbc.sql("select count(*) from received_replies where workspace_id=? and not removed").param(owner.workspaceId()).query(Long.class).single()).isEqualTo(5000);
        assertThat(jdbc.sql("select last_uid from mailbox_connections where account_id=?").param(account.getId()).query(Long.class).single()).isEqualTo(100);
        assertThat(inbox.connections()).singleElement().satisfies(connection->{
            assertThat(connection.error()).isEqualTo("STORAGE_LIMIT");
            assertThat(connection.state()).contains("Exclua").doesNotContain("senha");
        });
        var removedId=jdbc.sql("select id from received_replies where account_id=? and subject='First'").param(account.getId()).query(UUID.class).single();
        inbox.remove(removedId);

        assertThat(inbox.sync(account.getId(),owner.workspaceId()).imported()).isOne();
        assertThat(jdbc.sql("select last_uid from mailbox_connections where account_id=?").param(account.getId()).query(Long.class).single()).isEqualTo(105);
        assertThat(jdbc.sql("select count(*) from received_replies where workspace_id=? and not removed").param(owner.workspaceId()).query(Long.class).single()).isEqualTo(5000);
        assertThat(jdbc.sql("select removed from received_replies where id=?").param(removedId).query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("select count(*) from received_replies where account_id=? and subject='Next' and not removed").param(account.getId()).query(Long.class).single()).isOne();
    }
    @Test void stopDuringSyncDiscardsFetchedMessagesAndNetworkErrorsPreserveCursor() throws Exception {
        var account=gmail(); var job=outgoing(account); inbox.configure(account.getId(),true);
        when(reader.read(any(),anyString(),anyLong(),anyLong(),any())).thenThrow(new jakarta.mail.MessagingException("private-error-and-secret"));
        assertThat(inbox.sync(account.getId(),owner.workspaceId()).status()).isEqualTo("FAILED");
        assertThat(jdbc.sql("select last_uid from mailbox_connections where account_id=?").param(account.getId()).query(Long.class).single()).isZero();
        doAnswer(call->{
            inbox.configure(account.getId(),false);
            return new ImapReplyReader.Batch(71,100,java.util.List.of(new ImapReplyReader.Reply(
                new ReplyHeader(100,"recipient@example.test","Re",java.time.Instant.now(),java.util.List.of(job),"<reply@example.test>"),"Body")));
        }).when(reader).read(any(),anyString(),anyLong(),anyLong(),any());
        assertThat(inbox.sync(account.getId(),owner.workspaceId()).status()).isEqualTo("DISABLED");
        assertThat(inbox.list(0)).isEmpty();
        mvc.perform(get("/inbox").with(user(owner))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-error-and-secret"))));
    }
}
