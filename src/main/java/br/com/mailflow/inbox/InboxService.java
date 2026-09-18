package br.com.mailflow.inbox;

import br.com.mailflow.delivery.DispatchService;
import br.com.mailflow.security.CurrentWorkspace;
import br.com.mailflow.settings.smtp.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class InboxService {
    private final JdbcClient jdbc;
    private final CurrentWorkspace workspace;
    private final SmtpAccountService accounts;
    private final SmtpAccountRepository repository;
    private final SecretProtector protector;
    private final ImapReplyReader reader;
    private final TransactionTemplate transactions;
    public InboxService(JdbcClient jdbc,CurrentWorkspace workspace,SmtpAccountService accounts,SmtpAccountRepository repository,
        SecretProtector protector,ImapReplyReader reader,PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.workspace=workspace; this.accounts=accounts; this.repository=repository; this.protector=protector; this.reader=reader;
        transactions=new TransactionTemplate(manager);
    }
    public record SyncResult(String status,int imported) {}
    public record Connection(UUID accountId,String email,boolean enabled,boolean supported,Instant checkedAt,String error) {
        public String state() {
            if(!supported) return "Leitura disponível para contas Gmail configuradas com senha de aplicativo.";
            if("IDENTITY_CHANGED".equals(error)) return "A conta mudou. Autorize a leitura novamente.";
            if("SYNC_FAILED".equals(error)) return "Não foi possível atualizar. Confira a senha de aplicativo e o acesso ao Gmail.";
            if("STORAGE_LIMIT".equals(error)) return "Limite de 5.000 respostas locais. Exclua cópias que não precisa para continuar.";
            return enabled?"Conectado para leitura de respostas":"Leitura desligada";
        }
    }
    public record StoredReply(UUID id,UUID messageId,String from,String subject,String text,Instant receivedAt) {}
    private record Cursor(boolean enabled,String fingerprint,long validity,long lastUid,Instant lockedUntil) {}
    private record Claim(SmtpAccount account,UUID token,long validity,long lastUid,String protectedSecret) {}

    public void configure(UUID id,boolean enabled) {
        transactions.executeWithoutResult(status->{
            var account=accounts.get(id);
            if(enabled && !ImapReplyReader.supports(account)) throw new IllegalArgumentException("Configure seu Gmail com uma senha de aplicativo em Meu e-mail antes de conectar respostas.");
            String fingerprint=DispatchService.fingerprint(account);
            jdbc.sql("""
                insert into mailbox_connections(account_id,workspace_id,enabled,account_fingerprint) values (?,?,?,?)
                on conflict (account_id,workspace_id) do update set enabled=excluded.enabled,
                    last_uid=case when mailbox_connections.account_fingerprint=excluded.account_fingerprint then mailbox_connections.last_uid else 0 end,
                    uid_validity=case when mailbox_connections.account_fingerprint=excluded.account_fingerprint then mailbox_connections.uid_validity else 0 end,
                    account_fingerprint=excluded.account_fingerprint,last_error_code=null,locked_until=null,lock_token=null
                """).params(id,workspace.id(),enabled,fingerprint).update();
        });
    }
    public List<Connection> connections() {
        return accounts.list().stream().map(account->{
            var state=jdbc.sql("select enabled,last_checked_at,last_error_code from mailbox_connections where account_id=? and workspace_id=?")
                .params(account.getId(),workspace.id()).query((rs,n)->new Connection(account.getId(),account.getDefaultSender(),rs.getBoolean("enabled"),
                    ImapReplyReader.supports(account),rs.getTimestamp("last_checked_at")==null?null:rs.getTimestamp("last_checked_at").toInstant(),rs.getString("last_error_code"))).optional();
            return state.orElse(new Connection(account.getId(),account.getDefaultSender(),false,ImapReplyReader.supports(account),null,null));
        }).toList();
    }
    private Cursor cursor(UUID accountId,UUID workspaceId) {
        return jdbc.sql("select enabled,account_fingerprint,uid_validity,last_uid,locked_until from mailbox_connections where account_id=? and workspace_id=? for update")
            .params(accountId,workspaceId).query((rs,n)->new Cursor(rs.getBoolean("enabled"),rs.getString("account_fingerprint"),rs.getLong("uid_validity"),rs.getLong("last_uid"),
                rs.getTimestamp("locked_until")==null?null:rs.getTimestamp("locked_until").toInstant())).optional().orElse(null);
    }
    private Claim claim(UUID accountId,UUID workspaceId) {
        return transactions.execute(status->{
            var cursor=cursor(accountId,workspaceId); if(cursor==null || !cursor.enabled()) return null;
            var account=repository.findByIdAndWorkspaceId(accountId,workspaceId).orElse(null);
            if(account==null || !ImapReplyReader.supports(account) || !cursor.fingerprint().equals(DispatchService.fingerprint(account))) {
                jdbc.sql("update mailbox_connections set enabled=false,last_error_code='IDENTITY_CHANGED',lock_token=null,locked_until=null where account_id=? and workspace_id=?")
                    .params(accountId,workspaceId).update(); return null;
            }
            if(cursor.lockedUntil()!=null && cursor.lockedUntil().isAfter(Instant.now())) return new Claim(account,null,0,0,null);
            long total=jdbc.sql("select count(*) from received_replies where workspace_id=? and not removed").param(workspaceId).query(Long.class).single();
            if(total>=5000) {
                jdbc.sql("update mailbox_connections set last_error_code='STORAGE_LIMIT' where account_id=? and workspace_id=?").params(accountId,workspaceId).update(); return new Claim(account,null,0,0,null);
            }
            var token=UUID.randomUUID();
            jdbc.sql("update mailbox_connections set lock_token=?,locked_until=current_timestamp + interval '3 minutes' where account_id=? and workspace_id=?")
                .params(token,accountId,workspaceId).update();
            String secret=jdbc.sql("select secret_reference from smtp_accounts where id=? and workspace_id=?").params(accountId,workspaceId).query(String.class).single();
            return new Claim(account,token,cursor.validity(),cursor.lastUid(),secret);
        });
    }
    // workspaceId comes from the authenticated controller or from persisted, explicitly authorized connections.
    public SyncResult sync(UUID accountId,UUID workspaceId) {
        var claim=claim(accountId,workspaceId); if(claim==null) return new SyncResult("DISABLED",0);
        if(claim.token()==null) return new SyncResult("BUSY",0);
        try {
            var batch=reader.read(claim.account(),protector.unprotect(claim.protectedSecret()),claim.validity(),claim.lastUid(),header->related(accountId,workspaceId,header)!=null);
            return finish(accountId,workspaceId,claim,batch);
        } catch(Exception ex) {
            jdbc.sql("update mailbox_connections set last_error_code='SYNC_FAILED',last_checked_at=current_timestamp,locked_until=null,lock_token=null where account_id=? and workspace_id=? and lock_token=?")
                .params(accountId,workspaceId,claim.token()).update();
            return new SyncResult("FAILED",0);
        }
    }
    private UUID related(UUID accountId,UUID workspaceId,ReplyHeader header) {
        if(header.references().isEmpty() || header.references().size()>20) return null;
        var params=new ArrayList<Object>(); params.add(accountId); params.add(workspaceId); params.add(header.from()); params.addAll(header.references());
        return jdbc.sql("""
            select m.id from delivery_jobs j join message_recipients r on r.id=j.message_recipient_id and r.workspace_id=j.workspace_id
            join messages m on m.id=r.message_id and m.workspace_id=r.workspace_id
            where m.smtp_account_id=? and m.workspace_id=? and lower(r.email)=lower(?) and m.new_flow and m.confirmed_at is not null
                and m.test_of is null and j.attempt_count>0 and j.id in (
            """+String.join(",",Collections.nCopies(header.references().size(),"?"))+") order by j.available_at desc limit 1")
            .params(params).query(UUID.class).optional().orElse(null);
    }
    private SyncResult finish(UUID accountId,UUID workspaceId,Claim claim,ImapReplyReader.Batch batch) {
        if(batch.validity()<=0 || batch.lastUid()<0 || batch.replies().size()>200) throw new IllegalArgumentException("Cursor inválido.");
        return transactions.execute(status->{
            var cursor=cursor(accountId,workspaceId);
            var account=repository.findByIdAndWorkspaceId(accountId,workspaceId).orElse(null);
            var token=jdbc.sql("select lock_token from mailbox_connections where account_id=? and workspace_id=?").params(accountId,workspaceId).query(UUID.class).optional().orElse(null);
            if(cursor==null || !cursor.enabled() || !claim.token().equals(token) || account==null || !ImapReplyReader.supports(account)
                || !cursor.fingerprint().equals(DispatchService.fingerprint(account))) return new SyncResult("DISABLED",0);
            // Serialize workspace quota checks across different configured accounts.
            jdbc.sql("select id from workspaces where id=? for update").param(workspaceId).query(UUID.class).single();
            int imported=0; long processedUid=batch.lastUid(); boolean storageLimit=false;
            for(var reply:batch.replies().stream().sorted(Comparator.comparingLong(r->r.header().uid())).toList()) {
                if(reply.header().uid()<=0 || reply.header().uid()>batch.lastUid()) continue;
                var message=related(accountId,workspaceId,reply.header()); if(message==null) continue;
                String identity=reply.header().messageId().isBlank()?batch.validity()+":"+reply.header().uid():reply.header().messageId();
                var key=hash(identity);
                boolean exists=jdbc.sql("select exists(select 1 from received_replies where account_id=? and workspace_id=? and incoming_key=?)").params(accountId,workspaceId,key).query(Boolean.class).single();
                if(exists) continue;
                if(jdbc.sql("select count(*) from received_replies where workspace_id=? and not removed").param(workspaceId).query(Long.class).single()>=5000) {
                    // Keep this UID pending so deleting a local copy permits a later import.
                    processedUid=reply.header().uid()-1; storageLimit=true; break;
                }
                String body=reply.text()==null?"":reply.text();
                // PostgreSQL text rejects NUL. One hostile body must not poison the entire cursor.
                body=body.substring(0,Math.min(body.length(),50000)).replace('\0',' ');
                imported+=jdbc.sql("""
                    insert into received_replies(id,workspace_id,account_id,message_id,incoming_key,from_email,subject,body_text,received_at)
                    values (?,?,?,?,?,?,?,?,?) on conflict (account_id,workspace_id,incoming_key) do nothing
                    """).params(UUID.randomUUID(),workspaceId,accountId,message,key,reply.header().from(),reply.header().subject(),body,Timestamp.from(reply.header().receivedAt())).update();
            }
            long last=cursor.validity()==batch.validity()?Math.max(cursor.lastUid(),processedUid):processedUid;
            jdbc.sql("update mailbox_connections set uid_validity=?,last_uid=?,last_checked_at=current_timestamp,last_error_code=?,locked_until=null,lock_token=null where account_id=? and workspace_id=? and lock_token=?")
                .params(batch.validity(),last,storageLimit?"STORAGE_LIMIT":null,accountId,workspaceId,claim.token()).update();
            return new SyncResult("UPDATED",imported);
        });
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public List<StoredReply> list(int page) {
        if(page<0 || page>10000) throw new IllegalArgumentException("Página inválida.");
        return jdbc.sql("select id,message_id,from_email,subject,body_text,received_at from received_replies where workspace_id=? and not removed order by received_at desc,id limit 51 offset ?")
            .params(workspace.id(),page*50).query((rs,n)->new StoredReply(rs.getObject("id",UUID.class),rs.getObject("message_id",UUID.class),rs.getString("from_email"),rs.getString("subject"),rs.getString("body_text"),rs.getTimestamp("received_at").toInstant())).list();
    }
    public void remove(UUID id) {
        if(jdbc.sql("update received_replies set removed=true,body_text='',subject='',from_email='' where id=? and workspace_id=? and not removed")
            .params(id,workspace.id()).update()!=1) throw new EntityNotFoundException("Resposta não encontrada.");
    }
    public void tick() {
        var ids=jdbc.sql("select account_id,workspace_id from mailbox_connections where enabled order by last_checked_at nulls first limit 5")
            .query((rs,n)->new UUID[]{rs.getObject("account_id",UUID.class),rs.getObject("workspace_id",UUID.class)}).list();
        for(var idsForAccount:ids) sync(idsForAccount[0],idsForAccount[1]);
    }
}
