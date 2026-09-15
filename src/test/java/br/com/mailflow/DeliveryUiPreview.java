package br.com.mailflow;

import br.com.mailflow.delivery.EmailGateway;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;

/** Browser fixture: synthetic PostgreSQL and a replacing transport that cannot access the network. */
public class DeliveryUiPreview {
    public static void main(String[] args) throws Exception {
        var postgres=EmbeddedPostgres.builder().setServerConfig("listen_addresses","127.0.0.1").start();
        var app=new SpringApplication(MailFlowApplication.class);
        app.addInitializers(context -> context.addBeanFactoryPostProcessor(beanFactory -> {
            var registry=(org.springframework.beans.factory.support.BeanDefinitionRegistry)beanFactory;
            registry.removeBeanDefinition("secureSmtpGateway");
            beanFactory.registerSingleton("syntheticGateway",(EmailGateway)(account,message)->EmailGateway.Outcome.ACCEPTED);
        }));
        var application=app.run("--spring.datasource.url="+postgres.getJdbcUrl("postgres","postgres"),"--spring.datasource.username=postgres","--spring.datasource.password=",
            "--spring.sql.init.mode=never","--spring.flyway.enabled=true","--spring.jpa.hibernate.ddl-auto=validate","--server.port=8080","--server.address=127.0.0.1","--spring.quartz.auto-startup=false","--app.delivery.enabled=true");
        Runtime.getRuntime().addShutdownHook(new Thread(()->{application.close();try{postgres.close();}catch(java.io.IOException ignored){}}));
        while(!java.nio.file.Files.exists(java.nio.file.Path.of(".local-tools/stop-delivery-preview")))Thread.sleep(500);
        application.close();postgres.close();System.exit(0);
    }
}
