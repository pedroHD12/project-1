package br.com.mailflow.draft;

import br.com.mailflow.contact.ContactService;
import br.com.mailflow.delivery.DispatchForm;
import br.com.mailflow.security.CurrentWorkspace;
import br.com.mailflow.settings.smtp.SmtpAccountService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SavedDraftService {
    private final JdbcClient jdbc;
    private final CurrentWorkspace workspace;
    private final SmtpAccountService accounts;
    private final ContactService contacts;
    public SavedDraftService(JdbcClient jdbc,CurrentWorkspace workspace,SmtpAccountService accounts,ContactService contacts) {
        this.jdbc=jdbc; this.workspace=workspace; this.accounts=accounts; this.contacts=contacts;
    }
    public record Draft(UUID id,long version,Instant updatedAt,DispatchForm form) {
        public String title() { return form.getSubject().isBlank()?"Sem assunto":form.getSubject(); }
    }
    private void validate(DispatchForm form) {
        if(value(form.getSubject()).length()>998 || value(form.getBodyText()).length()>50000 || value(form.getBodyHtml()).length()>50000
            || value(form.getScheduledAt()).length()>30 || value(form.getTimezone()).length()>80
            || !Set.of("NOW","ONCE","DAILY","WEEKLY").contains(value(form.getMode())) || form.getOccurrences()<1 || form.getOccurrences()>30)
            throw new IllegalArgumentException("Reduza o conteúdo e confira as opções do rascunho.");
        try { ZoneId.of(value(form.getTimezone())); }
        catch(java.time.DateTimeException ex) { throw new IllegalArgumentException("Escolha um fuso horário válido."); }
        if(form.getAccountId()!=null) accounts.get(form.getAccountId());
        if(form.getContactIds()==null || form.getContactIds().size()>20 || form.getContactIds().contains(null))
            throw new IllegalArgumentException("Selecione até 20 contatos válidos.");
        for(var contactId:form.getContactIds()) contacts.get(contactId);
    }
    private static String value(String text) { return text==null?"":text; }
    private static String contactIds(DispatchForm form) {
        return form.getContactIds().stream().distinct().map(UUID::toString).collect(Collectors.joining(","));
    }
    @Transactional
    public UUID create(DispatchForm form) {
        validate(form);
        jdbc.sql("select id from workspaces where id=? for update").param(workspace.id()).query(UUID.class).single();
        if(jdbc.sql("select count(*) from saved_drafts where workspace_id=?").param(workspace.id()).query(Long.class).single()>=1000)
            throw new IllegalArgumentException("Você tem 1.000 rascunhos. Exclua os que não precisa para salvar outro.");
        var id=UUID.randomUUID();
        jdbc.sql("""
            insert into saved_drafts(id,workspace_id,account_id,contact_ids,subject,body_text,body_html,delivery_mode,scheduled_at,delivery_timezone,occurrence_count)
            values (?,?,?,?,?,?,?,?,?,?,?)
            """).params(id,workspace.id(),form.getAccountId(),contactIds(form),value(form.getSubject()),value(form.getBodyText()),value(form.getBodyHtml()),form.getMode(),value(form.getScheduledAt()),form.getTimezone(),form.getOccurrences()).update();
        return id;
    }
    @Transactional
    public long update(UUID id,long version,DispatchForm form) {
        get(id); validate(form);
        int changed=jdbc.sql("""
            update saved_drafts set account_id=?,contact_ids=?,subject=?,body_text=?,body_html=?,delivery_mode=?,scheduled_at=?,delivery_timezone=?,occurrence_count=?,version=version+1,updated_at=current_timestamp
            where id=? and workspace_id=? and version=?
            """).params(form.getAccountId(),contactIds(form),value(form.getSubject()),value(form.getBodyText()),value(form.getBodyHtml()),form.getMode(),value(form.getScheduledAt()),form.getTimezone(),form.getOccurrences(),id,workspace.id(),version).update();
        if(changed!=1) throw new IllegalArgumentException("Esse rascunho mudou em outra aba. Reabra a versão salva antes de editar novamente.");
        return version+1;
    }
    @Transactional(readOnly=true)
    public Draft get(UUID id) {
        return jdbc.sql("select * from saved_drafts where id=? and workspace_id=?").params(id,workspace.id()).query(this::map).optional()
            .orElseThrow(()->new EntityNotFoundException("Rascunho não encontrado."));
    }
    @Transactional(readOnly=true)
    public List<Draft> list(int page) {
        if(page<0 || page>10000) throw new IllegalArgumentException("Página inválida.");
        return jdbc.sql("select * from saved_drafts where workspace_id=? order by updated_at desc,id limit 51 offset ?")
            .params(workspace.id(),page*50).query(this::map).list();
    }
    private Draft map(java.sql.ResultSet rs,int row) throws java.sql.SQLException {
        var form=new DispatchForm(); form.setAccountId(rs.getObject("account_id",UUID.class));
        var ids=rs.getString("contact_ids"); form.setContactIds(ids.isBlank()?List.of():Arrays.stream(ids.split(",")).map(UUID::fromString).toList());
        form.setSubject(rs.getString("subject")); form.setBodyText(rs.getString("body_text")); form.setBodyHtml(rs.getString("body_html"));
        form.setMode(rs.getString("delivery_mode")); form.setScheduledAt(rs.getString("scheduled_at")); form.setTimezone(rs.getString("delivery_timezone")); form.setOccurrences(rs.getInt("occurrence_count"));
        return new Draft(rs.getObject("id",UUID.class),rs.getLong("version"),rs.getTimestamp("updated_at").toInstant(),form);
    }
    @Transactional
    public void delete(UUID id) {
        if(jdbc.sql("delete from saved_drafts where id=? and workspace_id=?").params(id,workspace.id()).update()!=1)
            throw new EntityNotFoundException("Rascunho não encontrado.");
    }
}
