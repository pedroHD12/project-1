package br.com.mailflow;

import br.com.mailflow.contact.*;
import br.com.mailflow.delivery.*;
import br.com.mailflow.draft.SavedDraftService;
import br.com.mailflow.inbox.*;
import br.com.mailflow.security.*;
import br.com.mailflow.settings.smtp.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.file.*;
import java.util.*;

/** Disposable browser fixture: synthetic database, fake transports, no real account or mailbox access. */
public class WorkspaceUiPreview {
    public static void main(String[] args) throws Exception {
        try(var postgres=EmbeddedPostgres.builder().setServerConfig("listen_addresses","127.0.0.1").start()) {
            var app=new SpringApplication(MailFlowApplication.class);
            app.addInitializers(context->context.addBeanFactoryPostProcessor(beanFactory->{
                var registry=(org.springframework.beans.factory.support.BeanDefinitionRegistry)beanFactory;
                registry.removeBeanDefinition("secureSmtpGateway");
                registry.removeBeanDefinition("imapReplyReader");
                beanFactory.registerSingleton("syntheticGateway",(EmailGateway)(account,message)->EmailGateway.Outcome.ACCEPTED);
                beanFactory.registerSingleton("syntheticReader",new ImapReplyReader() {
                    @Override public Batch read(SmtpAccount account,String secret,long validity,long uid,java.util.function.Predicate<ReplyHeader> related) {
                        return new Batch(71,Math.max(100,uid),List.of());
                    }
                });
            }));
            try(var application=app.run("--spring.datasource.url="+postgres.getJdbcUrl("postgres","postgres"),
                "--spring.datasource.username=postgres","--spring.datasource.password=","--server.port=8083","--server.address=127.0.0.1",
                "--spring.sql.init.mode=never","--spring.flyway.enabled=true","--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.thymeleaf.cache=false",
                "--spring.quartz.auto-startup=false","--app.delivery.enabled=false","--app.inbox.enabled=false")) {
                var registration=new RegistrationForm(); registration.setName("Pessoa de demonstração"); registration.setEmail("demo@example.test");
                registration.setPassword("Somente-demo-local-2026"); registration.setConfirmPassword(registration.getPassword());
                var users=application.getBean(AccountStore.class); users.register(registration);
                var owner=users.authenticate(registration.getEmail(),registration.getPassword());
                SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(owner,null,owner.getAuthorities()));
                var accountForm=new SmtpAccountForm(); accountForm.setDefaultSender("demonstracao@gmail.com"); accountForm.setPassword("synthetic-not-a-real-password");
                var account=application.getBean(SmtpAccountService.class).create(accountForm);
                var contactForm=new ContactForm(); contactForm.setDisplayName("Ana • Demonstração"); contactForm.setEmail("ana@example.test");
                var contact=application.getBean(ContactService.class).create(contactForm);
                var draft=new DispatchForm(); draft.setSubject("Nossa conversa da próxima semana"); draft.setBodyText("Olá, Ana!\n\nEstou organizando os detalhes do nosso encontro. Retomo esta mensagem depois.\n\nAté breve!");
                application.getBean(SavedDraftService.class).create(draft);
                draft.setAccountId(account.getId()); draft.setContactIds(List.of(contact.getId()));
                var dispatch=application.getBean(DispatchService.class); var message=dispatch.createDraft(draft); dispatch.confirm(message);
                var job=dispatch.get(message).jobs().getFirst().id();
                var jdbc=application.getBean(JdbcClient.class); jdbc.sql("update delivery_jobs set attempt_count=1,status='SENT' where id=?").param(job).update();
                application.getBean(InboxService.class).configure(account.getId(),true);
                jdbc.sql("insert into received_replies(id,workspace_id,account_id,message_id,incoming_key,from_email,subject,body_text,received_at) values (?,?,?,?,?,?,?,?,current_timestamp)")
                    .params(UUID.randomUUID(),owner.workspaceId(),account.getId(),message,"synthetic-reply","ana@example.test","Re: Nossa conversa da próxima semana","Olá! Para mim, quinta-feira às 14h funciona. Obrigada por organizar.\n\nAté lá,\nAna").update();
                SecurityContextHolder.clearContext();
                System.out.println("SYNTHETIC_PREVIEW_READY http://localhost:8083/login");
                while(!Files.exists(Path.of("target/stop-workspace-preview"))) Thread.sleep(500);
            }
        }
    }
}
