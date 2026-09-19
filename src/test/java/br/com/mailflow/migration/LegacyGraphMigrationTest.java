package br.com.mailflow.migration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class LegacyGraphMigrationTest {
    static final String A = "10000000-0000-0000-0000-000000000001";
    static final String B = "10000000-0000-0000-0000-000000000002";
    static final String SPACE_A = "00000000-0000-0000-0000-000000000001";
    static final String SPACE_B = "00000000-0000-0000-0000-000000000002";
    static final List<String> TABLES = List.of("smtp_accounts","contacts","contact_groups","contact_group_members","tags","contact_tags",
        "email_templates","messages","message_recipients","attachments","recurrence_rules","schedules","sequences","sequence_steps",
        "delivery_jobs","delivery_attempts","blocked_recipients","application_settings","audit_logs");
    static final String[][] REFERENCES = {
        {"contact_group_members","group_id"},{"contact_group_members","contact_id"},
        {"contact_tags","tag_id"},{"contact_tags","contact_id"},{"messages","template_id"},{"messages","smtp_account_id"},
        {"message_recipients","message_id"},{"message_recipients","contact_id"},{"attachments","message_id"},
        {"schedules","message_id"},{"schedules","recurrence_rule_id"},{"sequence_steps","sequence_id"},{"sequence_steps","template_id"},
        {"delivery_jobs","message_recipient_id"},{"delivery_attempts","delivery_job_id"}
    };

    @Test void completeLegacyGraphPreservesEveryRowAndEveryRelationship() throws Exception {
        try(var pg = EmbeddedPostgres.builder().setServerConfig("listen_addresses","127.0.0.1").start();
            var connection = pg.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
            Flyway.configure().dataSource(pg.getPostgresDatabase()).target("2").load().migrate();
            insertGraph(sql,A,null);
            // V1 accepted case variants. Upgrading must not delete them or abort the migration.
            sql.execute("insert into blocked_recipients(id,email,reason) values ('"+B+"','BLOCKED@example.test','MANUAL')");
            var before = snapshot(sql);
            Flyway.configure().dataSource(pg.getPostgresDatabase()).load().migrate();
            assertThat(snapshot(sql)).isEqualTo(before);
            assertThat(count(sql,"select count(*) from messages where new_flow or confirmed_at is not null"))
                .as("legacy messages must not be enrolled for automatic delivery").isZero();
            for(var table:TABLES) {
                assertThat(count(sql,"select count(*) from "+table+" where workspace_id is null or workspace_id <> '"+SPACE_A+"'"))
                    .as(table+" ownership backfill").isZero();
            }
            sql.execute("insert into workspaces(id,name) values ('"+SPACE_B+"','Other')");
            // Use another UUID for the second graph because the second blocked row already uses B.
            var otherId = "20000000-0000-0000-0000-000000000002";
            insertGraph(sql,otherId,SPACE_B);
            // Avoid unrelated unique constraints masking the cross-workspace foreign-key checks.
            sql.execute("update message_recipients set email = 'other@example.test' where workspace_id = '"+SPACE_B+"'");
            sql.execute("update sequence_steps set step_order = 2 where workspace_id = '"+SPACE_B+"'");
            sql.execute("update delivery_attempts set attempt_number = 2 where workspace_id = '"+SPACE_B+"'");
            for(var reference:REFERENCES) {
                var query = "update "+reference[0]+" set "+reference[1]+" = '"+otherId+"' where workspace_id = '"+SPACE_A+"'";
                assertThatThrownBy(() -> sql.execute(query)).as(String.join(".",reference))
                    .isInstanceOf(SQLException.class).extracting(e -> ((SQLException)e).getSQLState()).isEqualTo("23503");
            }
            // Original CASCADE, SET NULL and RESTRICT behavior must survive the additional FKs.
            assertThatThrownBy(() -> sql.execute("delete from smtp_accounts where id = '"+A+"'"))
                .isInstanceOf(SQLException.class).extracting(e -> ((SQLException)e).getSQLState()).isEqualTo("23503");
            sql.execute("delete from contacts where id = '"+A+"'");
            assertThat(count(sql,"select count(*) from contact_group_members where workspace_id = '"+SPACE_A+"'" )).isZero();
            assertThat(count(sql,"select count(*) from contact_tags where workspace_id = '"+SPACE_A+"'" )).isZero();
            assertThat(count(sql,"select count(*) from message_recipients where id = '"+A+"' and contact_id is null" )).isEqualTo(1);
            sql.execute("delete from recurrence_rules where id = '"+A+"'");
            assertThat(count(sql,"select count(*) from schedules where id = '"+A+"' and recurrence_rule_id is null" )).isEqualTo(1);
            assertThatThrownBy(() -> sql.execute("delete from email_templates where id = '"+A+"'"))
                .isInstanceOf(SQLException.class).extracting(e -> ((SQLException)e).getSQLState()).isEqualTo("23503");
            sql.execute("delete from sequences where id = '"+A+"'");
            sql.execute("delete from email_templates where id = '"+A+"'");
            assertThat(count(sql,"select count(*) from messages where id = '"+A+"' and template_id is null" )).isEqualTo(1);
            sql.execute("delete from messages where id = '"+A+"'");
            for(var table:List.of("message_recipients","attachments","schedules","delivery_jobs","delivery_attempts"))
                assertThat(count(sql,"select count(*) from "+table+" where workspace_id = '"+SPACE_A+"'" )).as(table+" cascade").isZero();
            for(var table:TABLES)
                assertThat(count(sql,"select count(*) from "+table+" where workspace_id = '"+SPACE_B+"'" )).as(table+" other owner preserved").isEqualTo(1);
        }
    }

    static long count(Statement sql,String query) throws SQLException {
        try(var rows=sql.executeQuery(query)){ rows.next(); return rows.getLong(1); }
    }
    static Map<String,List<String>> snapshot(Statement sql) throws SQLException {
        var result = new TreeMap<String,List<String>>();
        for(var table:TABLES){
            var values=new ArrayList<String>();
            // Compare every original value; additive V3/V4 fields are not legacy data changes.
            var addedColumns = switch (table) {
                case "messages" -> " - ARRAY['new_flow','confirmed_at','from_email','account_fingerprint','delivery_mode','occurrence_count','planned_at','delivery_timezone','test_of']";
                case "message_recipients" -> " - ARRAY['rendered_subject','rendered_text','rendered_html']";
                case "delivery_jobs" -> " - 'late_delivery'";
                default -> "";
            };
            try(var rows=sql.executeQuery("select (to_jsonb(t) - 'workspace_id'"+addedColumns+")::text from "+table+" t")) {
                while(rows.next()) values.add(rows.getString(1));
            }
            Collections.sort(values); result.put(table,values);
        }
        return result;
    }
    static void insertGraph(Statement sql,String id,String workspace) throws SQLException {
        var statements=List.of(
            "insert into smtp_accounts(id,name,host,port,encryption_mode) values ('%1$s','Account','smtp.example.test',587,'STARTTLS')",
            "insert into contacts(id,email) values ('%1$s','contact@example.test')",
            "insert into contact_groups(id,name) values ('%1$s','Group')",
            "insert into contact_group_members(group_id,contact_id) values ('%1$s','%1$s')",
            "insert into tags(id,name) values ('%1$s','Tag')",
            "insert into contact_tags(contact_id,tag_id) values ('%1$s','%1$s')",
            "insert into email_templates(id,name,subject,body_text) values ('%1$s','Model','Subject','Body')",
            "insert into messages(id,template_id,smtp_account_id,name,subject,body_text) values ('%1$s','%1$s','%1$s','Message','Subject','Body')",
            "insert into message_recipients(id,message_id,contact_id,email) values ('%1$s','%1$s','%1$s','contact@example.test')",
            "insert into attachments(id,message_id,file_name,file_path,file_size) values ('%1$s','%1$s','test.txt','synthetic/test.txt',0)",
            "insert into recurrence_rules(id,recurrence_type) values ('%1$s','DAILY')",
            "insert into schedules(id,message_id,recurrence_rule_id,scheduled_at) values ('%1$s','%1$s','%1$s',current_timestamp)",
            "insert into sequences(id,name) values ('%1$s','Sequence')",
            "insert into sequence_steps(id,sequence_id,template_id,step_order) values ('%1$s','%1$s','%1$s',1)",
            "insert into delivery_jobs(id,message_recipient_id,idempotency_key) values ('%1$s','%1$s','test-key')",
            "insert into delivery_attempts(id,delivery_job_id,attempt_number,started_at,outcome) values ('%1$s','%1$s',1,current_timestamp,'PROCESSING')",
            "insert into blocked_recipients(id,email,reason) values ('%1$s','blocked@example.test','MANUAL')",
            "insert into application_settings(setting_key,setting_value) values ('test','{}')",
            "insert into audit_logs(id,event_type,entity_id) values ('%1$s','TEST','%1$s')");
        for(var statement:statements){
            var query=statement.formatted(id);
            if(workspace!=null) query=query.replaceFirst("\\(","(workspace_id,").replaceFirst("values \\(","values ('"+workspace+"',");
            sql.execute(query);
        }
    }
}
