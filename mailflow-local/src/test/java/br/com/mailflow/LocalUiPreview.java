package br.com.mailflow;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;

/** Disposable, loopback-only browser verification. Never uses the user's configured database. */
public class LocalUiPreview {
    public static void main(String[] args) throws Exception {
        var postgres = EmbeddedPostgres.builder().setPort(0).start();
        var application = new SpringApplication(MailFlowApplication.class).run(
            "--spring.datasource.url=" + postgres.getJdbcUrl("postgres", "postgres"),
            "--spring.datasource.username=postgres", "--spring.datasource.password=",
            "--spring.sql.init.mode=never", "--spring.flyway.enabled=true", "--spring.jpa.hibernate.ddl-auto=validate",
            "--server.port=8080", "--server.address=127.0.0.1", "--spring.quartz.auto-startup=false");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            application.close();
            try { postgres.close(); } catch (java.io.IOException ignored) { }
        }));
        while (!java.nio.file.Files.exists(java.nio.file.Path.of(".local-tools/stop-ui-preview"))) {
            Thread.sleep(500);
        }
        application.close();
        postgres.close();
        System.exit(0);
    }
}
