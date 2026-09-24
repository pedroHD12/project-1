package br.com.mailflow.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

@Component
public class CloudStartupValidator implements SmartInitializingSingleton {

    private final AppRuntimeProperties runtime;
    private final Environment environment;

    public CloudStartupValidator(AppRuntimeProperties runtime, Environment environment) {
        this.runtime = runtime;
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validate(runtime);
        validateProfileMode(runtime, Arrays.asList(environment.getActiveProfiles()).contains("cloud"));
    }

    static void validate(AppRuntimeProperties runtime) {
        if (!runtime.isCloud()) {
            return;
        }
        var databaseUrl = runtime.databaseUrl();
        var queryAt = databaseUrl.indexOf('?');
        var sslModes = queryAt < 0 ? java.util.List.<String>of() : Arrays.stream(databaseUrl.substring(queryAt + 1).split("&"))
                .map(part -> part.split("=", 2))
                .filter(pair -> pair.length == 2 && "sslmode".equalsIgnoreCase(pair[0]))
                .map(pair -> pair[1].toLowerCase(Locale.ROOT))
                .toList();
        if (sslModes.size() != 1 || !"verify-full".equals(sslModes.getFirst())) {
            throw new IllegalStateException("Cloud exige PostgreSQL com verificação TLS (sslmode=verify-full).");
        }
        var rootCertificates = Arrays.stream(databaseUrl.substring(queryAt + 1).split("&"))
                .map(part -> part.split("=", 2))
                .filter(pair -> pair.length == 2 && "sslrootcert".equalsIgnoreCase(pair[0]))
                .map(pair -> pair[1])
                .toList();
        if (rootCertificates.size() != 1 || rootCertificates.getFirst().isBlank()) {
            throw new IllegalStateException("Cloud exige o certificado raiz PostgreSQL (sslrootcert).");
        }
    }

    static void validateProfileMode(AppRuntimeProperties runtime, boolean cloudProfileActive) {
        if (runtime.isCloud() != cloudProfileActive) {
            throw new IllegalStateException("APP_RUNTIME_MODE deve ser cloud somente com o perfil Spring cloud.");
        }
    }
}
