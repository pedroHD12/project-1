package br.com.mailflow.security;

import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@Profile("cloud")
public class CloudOwnerSetupValidator implements SmartLifecycle {

    private final OwnerSetupProperties owner;
    private final JdbcClient jdbc;
    private boolean running;

    public CloudOwnerSetupValidator(OwnerSetupProperties owner, JdbcClient jdbc) {
        this.owner = owner;
        this.jdbc = jdbc;
    }

    @Override
    public void start() {
        var hasOwner = jdbc.sql("select count(*) from app_users").query(Long.class).single() > 0;
        if (!hasOwner && (owner.email().isBlank() || owner.setupToken().isBlank())) {
            throw new IllegalStateException("OWNER_EMAIL e INITIAL_OWNER_SETUP_TOKEN são obrigatórios no modo cloud.");
        }
        running = true;
    }

    @Override public void stop() { running = false; }
    @Override public boolean isRunning() { return running; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MIN_VALUE; }
}
