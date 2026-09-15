package br.com.mailflow.migration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

class WorkspaceMigrationTest {
    @Test void preservesLegacyDataAndRejectsCrossWorkspaceReferences() throws Exception {
        try (var pg = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start()) {
            var ds = pg.getPostgresDatabase();
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("2").load().migrate();
            try (var c = ds.getConnection(); var sql = c.createStatement()) {
                sql.execute("insert into contacts(id,email) values ('10000000-0000-0000-0000-000000000001','same@example.test')");
                sql.execute("insert into smtp_accounts(id,name,host,port,encryption_mode) values ('20000000-0000-0000-0000-000000000001','Pessoal','smtp.example.test',587,'STARTTLS')");
                sql.execute("insert into email_templates(id,name,subject,body_text) values ('30000000-0000-0000-0000-000000000001','Modelo','Assunto','Texto')");
            }
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
            try (var c = ds.getConnection(); var sql = c.createStatement()) {
                try (var rows = sql.executeQuery("select workspace_id from contacts where id = '10000000-0000-0000-0000-000000000001'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("00000000-0000-0000-0000-000000000001");
                }
                sql.execute("insert into workspaces(id,name) values ('00000000-0000-0000-0000-000000000002','Outro')");
                sql.execute("insert into contacts(id,workspace_id,email) values ('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000002','same@example.test')");
                assertThatThrownBy(() -> sql.execute("insert into contacts(id,workspace_id,email) values ('10000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002','SAME@example.test')"))
                        .isInstanceOf(SQLException.class).extracting(e -> ((SQLException)e).getSQLState()).isEqualTo("23505");
                assertThatThrownBy(() -> sql.execute("insert into messages(id,workspace_id,template_id,name,subject,body_text) values ('40000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000001','X','X','X')"))
                        .isInstanceOf(SQLException.class).extracting(e -> ((SQLException)e).getSQLState()).isEqualTo("23503");
                assertThatThrownBy(() -> sql.execute("insert into contacts(id,email) values ('10000000-0000-0000-0000-000000000004','missing@example.test')"))
                        .isInstanceOf(SQLException.class).extracting(e -> ((SQLException)e).getSQLState()).isEqualTo("23502");
            }
        }
    }
}
