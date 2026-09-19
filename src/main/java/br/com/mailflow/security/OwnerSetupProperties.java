package br.com.mailflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

@ConfigurationProperties(prefix = "app.owner")
public record OwnerSetupProperties(String email, String setupToken) {

    public OwnerSetupProperties {
        email = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        setupToken = setupToken == null ? "" : setupToken;
    }
}
