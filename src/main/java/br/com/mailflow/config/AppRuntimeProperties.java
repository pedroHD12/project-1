package br.com.mailflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.runtime")
public record AppRuntimeProperties(String mode, String databaseUrl) {

    public AppRuntimeProperties {
        mode = mode == null ? "local" : mode.strip().toLowerCase(java.util.Locale.ROOT);
        databaseUrl = databaseUrl == null ? "" : databaseUrl.strip();
    }

    public boolean isCloud() {
        return "cloud".equals(mode);
    }
}
