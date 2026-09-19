package br.com.mailflow.settings.smtp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.credentials")
public record CredentialKeyProperties(String keyV1, String keyV0) {
}
