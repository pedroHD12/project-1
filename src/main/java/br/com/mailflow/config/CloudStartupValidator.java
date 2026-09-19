package br.com.mailflow.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class CloudStartupValidator implements SmartInitializingSingleton {

    private final AppRuntimeProperties runtime;

    public CloudStartupValidator(AppRuntimeProperties runtime) {
        this.runtime = runtime;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validate(runtime);
    }

    static void validate(AppRuntimeProperties runtime) {
        if (!runtime.isCloud()) {
            return;
        }
        var databaseUrl = runtime.databaseUrl().toLowerCase(Locale.ROOT);
        if (!databaseUrl.matches(".*[?&]sslmode=verify-full(?:&.*|$).*")) {
            throw new IllegalStateException("Cloud exige PostgreSQL com verificação TLS (sslmode=verify-full).");
        }
    }
}
