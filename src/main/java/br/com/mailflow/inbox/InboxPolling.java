package br.com.mailflow.inbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.inbox.enabled",havingValue="true",matchIfMissing=true)
public class InboxPolling {
    private final InboxService inbox;
    public InboxPolling(InboxService inbox) { this.inbox=inbox; }
    @Scheduled(fixedDelay=120000,initialDelay=60000) public void tick() {
        try { inbox.tick(); }
        catch(RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(InboxPolling.class).warn("Leitura de respostas indisponível. Confira o banco local."); }
    }
}
