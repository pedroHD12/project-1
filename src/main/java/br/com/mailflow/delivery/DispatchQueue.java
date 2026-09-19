package br.com.mailflow.delivery;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class DispatchQueue {
    private final JdbcClient jdbc;
    public DispatchQueue(JdbcClient jdbc) {this.jdbc=jdbc;}
    public record Work(UUID id,UUID workspaceId,UUID messageId,UUID accountId,String fingerprint,String from,
                       UUID contactId,boolean selfTest,String email,String subject,String text,String html,int attempt,UUID claimToken,boolean late) {}

    @Transactional
    public void recoverInterrupted() {
        jdbc.sql("""
            update delivery_jobs j set status='UNKNOWN',last_error_code='INTERRUPTED',updated_at=current_timestamp
            from message_recipients r,messages m where j.message_recipient_id=r.id and r.message_id=m.id
            and j.workspace_id=r.workspace_id and r.workspace_id=m.workspace_id and m.new_flow and m.confirmed_at is not null
            and j.status='PROCESSING' and j.locked_at < current_timestamp - interval '5 minutes'
            """).update();
        jdbc.sql("""
            update delivery_attempts a set outcome='UNKNOWN',finished_at=current_timestamp
            from delivery_jobs j where a.delivery_job_id=j.id and a.workspace_id=j.workspace_id
            and a.outcome='PROCESSING' and j.status='UNKNOWN'
            """).update();
    }

    @Transactional
    public Optional<Work> claim() {
        jdbc.sql("select id from delivery_worker_guard where id=1 for update").query(Integer.class).single();
        // Limits are persisted and shared by all application processes, including manual self-tests.
        long minute=jdbc.sql("select count(*) from delivery_attempts where started_at > current_timestamp - interval '1 minute'").query(Long.class).single();
        long day=jdbc.sql("select count(*) from delivery_attempts where started_at > current_timestamp - interval '24 hours'").query(Long.class).single();
        if(minute>=12 || day>=200) return Optional.empty();
        var candidate=jdbc.sql("""
            select j.id,j.workspace_id,m.id message_id,m.smtp_account_id,m.account_fingerprint,m.from_email,
                r.contact_id,m.test_of,r.email,r.rendered_subject,r.rendered_text,r.rendered_html,j.attempt_count,
                (j.late_delivery or j.available_at < current_timestamp - interval '15 minutes') late
            from delivery_jobs j join message_recipients r on r.id=j.message_recipient_id and r.workspace_id=j.workspace_id
            join messages m on m.id=r.message_id and m.workspace_id=r.workspace_id
            where m.new_flow and m.confirmed_at is not null and m.status='QUEUED'
                and j.status in ('PENDING','RETRY') and j.available_at<=current_timestamp
            order by j.available_at,j.id limit 1 for update of m skip locked
            """).query((rs,n)->new Work(rs.getObject("id",UUID.class),rs.getObject("workspace_id",UUID.class),rs.getObject("message_id",UUID.class),rs.getObject("smtp_account_id",UUID.class),rs.getString("account_fingerprint"),rs.getString("from_email"),rs.getObject("contact_id",UUID.class),rs.getObject("test_of")!=null,rs.getString("email"),rs.getString("rendered_subject"),rs.getString("rendered_text"),rs.getString("rendered_html"),rs.getInt("attempt_count")+1,UUID.randomUUID(),rs.getBoolean("late"))).optional();
        if(candidate.isEmpty()) return Optional.empty();
        var work=candidate.get();
        int changed=jdbc.sql("""
            update delivery_jobs set status='PROCESSING',attempt_count=attempt_count+1,late_delivery=late_delivery or ?,locked_by=?,locked_at=current_timestamp,updated_at=current_timestamp
            where id=? and workspace_id=? and status in ('PENDING','RETRY') and attempt_count<max_attempts
            """).params(work.late(),work.claimToken().toString(),work.id(),work.workspaceId()).update();
        if(changed!=1) return Optional.empty();
        jdbc.sql("insert into delivery_attempts(id,workspace_id,delivery_job_id,attempt_number,started_at,outcome) values (?,?,?,?,current_timestamp,'PROCESSING')")
            .params(UUID.randomUUID(),work.workspaceId(),work.id(),work.attempt()).update();
        return Optional.of(work);
    }
    @Transactional
    public boolean permitted(Work work) {
        var state=jdbc.sql("select status from messages where id=? and workspace_id=? and confirmed_at is not null and new_flow for update")
            .params(work.messageId(),work.workspaceId()).query(String.class).optional().orElse("CANCELLED");
        if("PAUSED".equals(state)) {
            // No network call has begun. Release this reservation without spending an SMTP attempt.
            int released=jdbc.sql("update delivery_jobs set status='PENDING',attempt_count=attempt_count-1,locked_at=null,locked_by=null,updated_at=current_timestamp where id=? and workspace_id=? and status='PROCESSING' and attempt_count=? and locked_by=?")
                .params(work.id(),work.workspaceId(),work.attempt(),work.claimToken().toString()).update();
            if(released==1) {
                jdbc.sql("delete from delivery_attempts where delivery_job_id=? and workspace_id=? and attempt_number=? and outcome='PROCESSING'").params(work.id(),work.workspaceId(),work.attempt()).update();
                jdbc.sql("insert into audit_logs(id,workspace_id,event_type,entity_type,entity_id) values (?,?,'DELIVERY_DEFERRED','delivery_job',?)").params(UUID.randomUUID(),work.workspaceId(),work.id()).update();
            }
            return false;
        }
        if(!"QUEUED".equals(state)) return false;
        // Renew and validate the exact reservation immediately before network I/O.
        // A worker recovered as UNKNOWN, or superseded by another claim, cannot send.
        int reserved=jdbc.sql("""
            update delivery_jobs set locked_at=current_timestamp,updated_at=current_timestamp
            where id=? and workspace_id=? and status='PROCESSING' and attempt_count=? and locked_by=?
            """).params(work.id(),work.workspaceId(),work.attempt(),work.claimToken().toString()).update();
        if(reserved!=1) return false;
        boolean blocked=jdbc.sql("select exists(select 1 from blocked_recipients where workspace_id=? and lower(email)=lower(?))")
            .params(work.workspaceId(),work.email()).query(Boolean.class).single();
        if(blocked) return false;
        if(work.selfTest()) return true;
        return jdbc.sql("select exists(select 1 from contacts where id=? and workspace_id=? and status='ACTIVE' and lower(email)=lower(?))")
            .params(work.contactId(),work.workspaceId(),work.email()).query(Boolean.class).single();
    }

    @Transactional
    public void finish(Work work,EmailGateway.Outcome outcome,boolean skipped) {
        // Parent-first locking matches pause/cancel/claim; network I/O is never inside this transaction.
        jdbc.sql("select id from messages where id=? and workspace_id=? for update").params(work.messageId(),work.workspaceId()).query(UUID.class).single();
        String state=skipped?"SKIPPED":switch(outcome) {
            case ACCEPTED -> work.late()?"SENT_LATE":"SENT"; case UNKNOWN -> "UNKNOWN";
            case REJECTED -> "FAILED"; case RETRYABLE -> work.attempt()<3?"RETRY":"FAILED";
        };
        String attemptOutcome=skipped?"PERMANENT_ERROR":switch(outcome) {
            case ACCEPTED -> "ACCEPTED"; case UNKNOWN -> "UNKNOWN"; case REJECTED -> "PERMANENT_ERROR"; case RETRYABLE -> "TEMPORARY_ERROR";
        };
        String code=skipped?"SAFETY_CHECK":switch(outcome) {case ACCEPTED -> "ACCEPTED";case UNKNOWN -> "ACK_UNKNOWN";case REJECTED -> "CHECK_CONFIGURATION";case RETRYABLE -> "CONNECTION_FAILED";};
        int changed=jdbc.sql("""
            update delivery_jobs set status=?,last_error_code=?,last_error_message=null,
                available_at=case when ?='RETRY' then current_timestamp + (? * interval '1 minute') else available_at end,
                completed_at=case when ?='RETRY' then null else current_timestamp end,updated_at=current_timestamp
            where id=? and workspace_id=? and status='PROCESSING' and attempt_count=? and locked_by=?
            """).params(state,code,state,work.attempt(),state,work.id(),work.workspaceId(),work.attempt(),work.claimToken().toString()).update();
        if(changed!=1) return; // A recovered UNKNOWN claim may not be turned into a retry by a late worker.
        jdbc.sql("update delivery_attempts set outcome=?,finished_at=current_timestamp,error_message=null where delivery_job_id=? and workspace_id=? and attempt_number=?")
            .params(attemptOutcome,work.id(),work.workspaceId(),work.attempt()).update();
        jdbc.sql("insert into audit_logs(id,workspace_id,event_type,entity_type,entity_id) values (?,?,?,'delivery_job',?)")
            .params(UUID.randomUUID(),work.workspaceId(),"DELIVERY_"+state,work.id()).update();
        jdbc.sql("""
            update messages m set status='COMPLETED',updated_at=current_timestamp where id=? and workspace_id=? and status='QUEUED'
            and not exists (select 1 from message_recipients r join delivery_jobs j on j.message_recipient_id=r.id and j.workspace_id=r.workspace_id
                where r.message_id=m.id and r.workspace_id=m.workspace_id and j.status in ('PENDING','RETRY','PROCESSING','MISSED'))
            """).params(work.messageId(),work.workspaceId()).update();
    }
}
