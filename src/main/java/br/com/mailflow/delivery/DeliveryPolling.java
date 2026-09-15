package br.com.mailflow.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.delivery.enabled",havingValue="true",matchIfMissing=true)
public class DeliveryPolling {
    private final DispatchWorker worker;
    public DeliveryPolling(DispatchWorker worker) {this.worker=worker;}
    @Scheduled(fixedDelay=5000,initialDelay=10000)
    public void tick() {
        try {worker.processOne();}
        catch(RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(DeliveryPolling.class).warn("Fila de envio indisponível. Nenhum detalhe sensível registrado; verifique o banco local.");
        }
    }
}
