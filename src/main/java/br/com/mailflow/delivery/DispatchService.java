package br.com.mailflow.delivery;

import br.com.mailflow.contact.*;
import br.com.mailflow.security.CurrentWorkspace;
import br.com.mailflow.settings.smtp.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DispatchService {
    private final JdbcClient jdbc;
    private final CurrentWorkspace workspace;
    private final ContactService contacts;
    private final SmtpAccountService accounts;
    private final Validator validator;

    public DispatchService(JdbcClient jdbc, CurrentWorkspace workspace, ContactService contacts, SmtpAccountService accounts, Validator validator) {
        this.jdbc=jdbc; this.workspace=workspace; this.contacts=contacts; this.accounts=accounts; this.validator=validator;
    }

    public record Recipient(UUID id, UUID contactId, String email, String name, String subject, String text, String html) {
        public String previewDocument() {
            return "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; base-uri 'none'; form-action 'none'\"></head><body>"+html+"</body></html>";
        }
    }
    public record Job(UUID id, String email, String status, int attempts, Instant when, String code) {
        public String label() { return statusLabel(status); }
        public boolean releasable() { return Set.of("FAILED","MISSED").contains(status) && attempts<3; }
    }
    public record Message(UUID id, UUID accountId, String status, String from, String fingerprint, String subject,
                          String text, String html, String mode, int occurrences, Instant plannedAt, String timezone, UUID testOf) {
        public String label() { return statusLabel(status); }
        public String plannedLabel() { return plannedAt == null ? "Agora" : DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(plannedAt.atZone(ZoneId.of(timezone)))+" · "+timezone; }
    }
    public record Details(Message message, List<Recipient> recipients, List<Job> jobs) {}
    public record Summary(UUID id, String subject, String status, String mode, Instant createdAt, long total, long sent, long attention) {
        public String label() { return statusLabel(status); }
    }
    public static String statusLabel(String status) {
        return switch(status) {
            case "DRAFT" -> "Rascunho"; case "QUEUED","PENDING" -> "Aguardando";
            case "PROCESSING" -> "Enviando"; case "SENT" -> "Aceito pelo provedor";
            case "SENT_LATE" -> "Enviado após o horário programado";
            case "RETRY" -> "Nova tentativa programada"; case "FAILED" -> "Não enviado";
            case "UNKNOWN" -> "Resultado incerto — confira sua caixa de e-mail";
            case "MISSED" -> "Horário perdido — precisa de revisão"; case "SKIPPED" -> "Não enviado por segurança";
            case "PAUSED" -> "Pausado"; case "CANCELLED" -> "Cancelado"; case "COMPLETED" -> "Concluído";
            default -> "Precisa de revisão";
        };
    }

    @Transactional
    public UUID createDraft(DispatchForm form) {
        if(!validator.validate(form).isEmpty()) throw new IllegalArgumentException("Confira os campos da mensagem e selecione de 1 a 20 contatos.");
        var account=accounts.get(form.getAccountId());
        if(!account.isEnabled() || account.getEncryptionMode()==EncryptionMode.NONE) throw new IllegalArgumentException("Habilite uma conta com conexão protegida em Meu e-mail.");
        String from=address(account.getDefaultSender());
        var dates=form.dates(Instant.now());
        if(jdbc.sql("select count(*) from messages where workspace_id=? and new_flow and status='DRAFT'").param(workspace.id()).query(Long.class).single()>=100)
            throw new IllegalArgumentException("Há 100 rascunhos. Cancele os que não precisa antes de criar outro.");
        var recipients=new ArrayList<Recipient>();
        var seen=new HashSet<String>();
        for(var contactId:form.getContactIds()) {
            if(contactId==null) throw new IllegalArgumentException("Selecione contatos válidos.");
            var contact=contacts.get(contactId);
            if(contact.getStatus()!=ContactStatus.ACTIVE || blocked(workspace.id(),contact.getEmail())) throw new IllegalArgumentException("Remova os contatos bloqueados ou descadastrados da seleção.");
            String email=address(contact.getEmail());
            if(!seen.add(email.toLowerCase(Locale.ROOT))) continue;
            var content=MessageContent.render(form.getSubject(),form.getBodyText(),form.getBodyHtml(),contact.getDisplayName(),email);
            recipients.add(new Recipient(UUID.randomUUID(),contactId,email,contact.getDisplayName(),content.subject(),content.text(),content.html()));
        }
        var id=UUID.randomUUID();
        insertMessage(id,account.getId(),from,fingerprint(account),form.getSubject(),form.getBodyText(),form.getBodyHtml(),form.getMode(),dates.size(),dates.getFirst(),form.getTimezone(),null);
        for(var recipient:recipients) insertRecipient(id,recipient);
        audit("MESSAGE_DRAFT",id);
        return id;
    }

    private void insertMessage(UUID id,UUID accountId,String from,String fingerprint,String subject,String text,String html,String mode,int count,Instant when,String timezone,UUID testOf) {
        jdbc.sql("""
            insert into messages(id,workspace_id,smtp_account_id,name,subject,body_text,body_html,new_flow,from_email,account_fingerprint,delivery_mode,occurrence_count,planned_at,delivery_timezone,test_of)
            values (?,?,?,?,?,?,?,true,?,?,?,?,?,?,?)
            """).params(id,workspace.id(),accountId,subject.substring(0,Math.min(subject.length(),160)),subject,text==null?"":text,html==null?"":html,from,fingerprint,mode,count,Timestamp.from(when),timezone,testOf).update();
    }
    private void insertRecipient(UUID messageId,Recipient r) {
        jdbc.sql("""
            insert into message_recipients(id,workspace_id,message_id,contact_id,email,display_name,rendered_subject,rendered_text,rendered_html)
            values (?,?,?,?,?,?,?,?,?)
            """).params(r.id(),workspace.id(),messageId,r.contactId(),r.email(),r.name(),r.subject(),r.text(),r.html()).update();
    }
    @Transactional(readOnly=true)
    public Details get(UUID id) { return new Details(message(id,false),recipients(id),jobs(id)); }

    private Message message(UUID id,boolean lock) {
        return jdbc.sql("""
            select id,smtp_account_id,status,from_email,account_fingerprint,subject,body_text,body_html,delivery_mode,occurrence_count,planned_at,delivery_timezone,test_of
            from messages where id=? and workspace_id=? and new_flow
            """+(lock?" for update":"")).params(id,workspace.id()).query((rs,n)->new Message(rs.getObject("id",UUID.class),rs.getObject("smtp_account_id",UUID.class),rs.getString("status"),rs.getString("from_email"),rs.getString("account_fingerprint"),rs.getString("subject"),rs.getString("body_text"),rs.getString("body_html"),rs.getString("delivery_mode"),rs.getInt("occurrence_count"),rs.getTimestamp("planned_at").toInstant(),rs.getString("delivery_timezone"),rs.getObject("test_of",UUID.class))).optional()
            .orElseThrow(()->new EntityNotFoundException("Mensagem não encontrada."));
    }
    private List<Recipient> recipients(UUID id) {
        return jdbc.sql("select id,contact_id,email,display_name,rendered_subject,rendered_text,rendered_html from message_recipients where message_id=? and workspace_id=? order by email limit 20")
            .params(id,workspace.id()).query((rs,n)->new Recipient(rs.getObject("id",UUID.class),rs.getObject("contact_id",UUID.class),rs.getString("email"),rs.getString("display_name"),rs.getString("rendered_subject"),rs.getString("rendered_text"),rs.getString("rendered_html"))).list();
    }
    private List<Job> jobs(UUID id) {
        return jdbc.sql("""
            select j.id,r.email,j.status,j.attempt_count,j.available_at,j.last_error_code from delivery_jobs j
            join message_recipients r on r.id=j.message_recipient_id and r.workspace_id=j.workspace_id
            where r.message_id=? and j.workspace_id=? order by j.available_at,r.email limit 601
            """).params(id,workspace.id()).query((rs,n)->new Job(rs.getObject("id",UUID.class),rs.getString("email"),rs.getString("status"),rs.getInt("attempt_count"),rs.getTimestamp("available_at").toInstant(),rs.getString("last_error_code"))).list();
    }
    @Transactional(readOnly=true)
    public List<Summary> list(String kind) {
        String filter=switch(kind) {case "schedules" -> " and m.delivery_mode<>'NOW'"; case "automations" -> " and m.delivery_mode in ('DAILY','WEEKLY')"; default -> "";};
        return jdbc.sql("""
            select m.id,m.subject,m.status,m.delivery_mode,m.created_at,count(j.id) total,
            count(j.id) filter(where j.status in ('SENT','SENT_LATE')) sent,
            count(j.id) filter(where j.status in ('UNKNOWN','FAILED','MISSED','SKIPPED')) attention
            from messages m left join message_recipients r on r.message_id=m.id and r.workspace_id=m.workspace_id
            left join delivery_jobs j on j.message_recipient_id=r.id and j.workspace_id=r.workspace_id
            where m.workspace_id=? and m.new_flow
            """+filter+" group by m.id order by m.created_at desc limit 100")
            .param(workspace.id()).query((rs,n)->new Summary(rs.getObject("id",UUID.class),rs.getString("subject"),rs.getString("status"),rs.getString("delivery_mode"),rs.getTimestamp("created_at").toInstant(),rs.getLong("total"),rs.getLong("sent"),rs.getLong("attention"))).list();
    }

    @Transactional
    public void confirm(UUID id) {
        var m=message(id,true);
        if(!"DRAFT".equals(m.status())) return;
        var account=accounts.get(m.accountId());
        if(!account.isEnabled() || !m.fingerprint().equals(fingerprint(account))) throw new IllegalArgumentException("A conta de envio mudou. Crie um novo rascunho para revisar os dados atuais.");
        var form=new DispatchForm(); form.setMode(m.mode()); form.setOccurrences(m.occurrences()); form.setTimezone(m.timezone());
        form.setScheduledAt(m.plannedAt().atZone(ZoneId.of(m.timezone())).toLocalDateTime().toString());
        var dates=form.dates(Instant.now());
        for(var r:recipients(id)) {
            if(blocked(workspace.id(),r.email())) throw new IllegalArgumentException("Um destinatário foi bloqueado. Crie outro rascunho sem esse contato.");
            if(m.testOf()==null) {
                if(r.contactId()==null) throw new IllegalArgumentException("Um contato foi excluído. Crie outro rascunho.");
                var c=contacts.get(r.contactId());
                if(c.getStatus()!=ContactStatus.ACTIVE || !r.email().equalsIgnoreCase(c.getEmail())) throw new IllegalArgumentException("Um contato mudou. Crie outro rascunho para revisar os destinatários.");
            }
            for(int i=0;i<dates.size();i++) {
                jdbc.sql("insert into delivery_jobs(id,workspace_id,message_recipient_id,idempotency_key,available_at) values (?,?,?,?,?)")
                    .params(UUID.randomUUID(),workspace.id(),r.id(),id+":"+r.id()+":"+i,Timestamp.from(dates.get(i))).update();
            }
        }
        jdbc.sql("update messages set status='QUEUED',confirmed_at=current_timestamp,planned_at=?,updated_at=current_timestamp where id=? and workspace_id=?")
            .params(Timestamp.from(dates.getFirst()),id,workspace.id()).update();
        audit("MESSAGE_CONFIRMED",id);
    }
    @Transactional
    public UUID selfTest(UUID id) {
        var m=message(id,true);
        if(!"DRAFT".equals(m.status()) || m.testOf()!=null) throw new IllegalArgumentException("O teste é feito antes da confirmação do envio.");
        var existing=jdbc.sql("select id from messages where test_of=? and workspace_id=?").params(id,workspace.id()).query(UUID.class).optional();
        if(existing.isPresent()) return existing.get();
        var email=address(workspace.principal().getUsername());
        var content=MessageContent.render("[TESTE] "+m.subject(),m.text(),m.html(),"Teste",email);
        var testId=UUID.randomUUID();
        insertMessage(testId,m.accountId(),m.from(),m.fingerprint(),content.subject(),content.text(),content.html(),"NOW",1,Instant.now(),m.timezone(),id);
        insertRecipient(testId,new Recipient(UUID.randomUUID(),null,email,"Teste",content.subject(),content.text(),content.html()));
        confirm(testId);
        return testId;
    }
    @Transactional
    public void cancel(UUID id) {
        message(id,true);
        jdbc.sql("update messages set status='CANCELLED',updated_at=current_timestamp where id=? and workspace_id=?").params(id,workspace.id()).update();
        jdbc.sql("""
            update delivery_jobs set status='CANCELLED',updated_at=current_timestamp where workspace_id=? and status in ('PENDING','RETRY','MISSED','FAILED')
            and message_recipient_id in (select id from message_recipients where message_id=? and workspace_id=?)
            """).params(workspace.id(),id,workspace.id()).update();
        audit("MESSAGE_CANCELLED",id);
    }
    @Transactional
    public void pause(UUID id,boolean paused) {
        var m=message(id,true);
        if(!(paused?Set.of("QUEUED","PAUSED"):Set.of("PAUSED","QUEUED")).contains(m.status())) throw new IllegalArgumentException("Este envio não pode ser pausado ou retomado.");
        jdbc.sql("update messages set status=?,updated_at=current_timestamp where id=? and workspace_id=?").params(paused?"PAUSED":"QUEUED",id,workspace.id()).update();
        audit(paused?"MESSAGE_PAUSED":"MESSAGE_RESUMED",id);
    }
    @Transactional
    public void release(UUID messageId,UUID jobId) {
        var m=message(messageId,true);
        if(Set.of("CANCELLED","DRAFT","PAUSED").contains(m.status())) throw new IllegalArgumentException("Retome a fila antes de liberar uma tentativa. Envios cancelados não podem ser retomados.");
        int changed=jdbc.sql("""
            update delivery_jobs set status='RETRY',available_at=current_timestamp,last_error_code=null,updated_at=current_timestamp
            where id=? and workspace_id=? and status in ('MISSED','FAILED') and attempt_count<max_attempts
            and message_recipient_id in (select id from message_recipients where message_id=? and workspace_id=?)
            """).params(jobId,workspace.id(),messageId,workspace.id()).update();
        if(changed!=1) throw new IllegalArgumentException("Esta tentativa não pode ser repetida com segurança.");
        jdbc.sql("update messages set status='QUEUED',updated_at=current_timestamp where id=? and workspace_id=?").params(messageId,workspace.id()).update();
        audit("DELIVERY_RELEASED",jobId);
    }
    boolean blocked(UUID workspaceId,String email) {
        return jdbc.sql("select exists(select 1 from blocked_recipients where workspace_id=? and lower(email)=lower(?))").params(workspaceId,email).query(Boolean.class).single();
    }
    private void audit(String event,UUID id) {
        jdbc.sql("insert into audit_logs(id,workspace_id,event_type,entity_type,entity_id) values (?,?,?,'message',?)").params(UUID.randomUUID(),workspace.id(),event,id).update();
    }
    public static String address(String email) {
        try {
            if(email==null || email.length()>320 || email.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException();
            var parsed=new jakarta.mail.internet.InternetAddress(email,true); parsed.validate();
            if(parsed.getPersonal()!=null || !parsed.getAddress().equals(email) || !email.contains("@")) throw new IllegalArgumentException();
            return email;
        } catch(Exception ex) { throw new IllegalArgumentException("Confira o endereço de e-mail do remetente e dos contatos."); }
    }
    public static String fingerprint(SmtpAccount account) {
        String data=account.getHost()+"\n"+account.getPort()+"\n"+account.getEncryptionMode()+"\n"+account.getUsername()+"\n"+account.getDefaultSender();
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 indisponível"); }
    }
}
