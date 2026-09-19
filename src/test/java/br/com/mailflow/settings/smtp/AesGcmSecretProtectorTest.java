package br.com.mailflow.settings.smtp;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmSecretProtectorTest {

    private static final String V1 = key("mailflow-cloud-key-material-v1-32b");
    private static final String V0 = key("mailflow-cloud-key-material-v0-32b");

    @Test
    void encryptsWithoutLeakingPlaintextAndRoundTrips() {
        var protector = new AesGcmSecretProtector(new CredentialKeyRing(V1, V0));

        var encrypted = protector.protect("application-password");

        assertThat(encrypted).startsWith("aesgcm:v1:").doesNotContain("application-password");
        assertThat(protector.unprotect(encrypted)).isEqualTo("application-password");
    }

    @Test
    void decryptsPreviousKeyForLazyRotation() {
        var previous = new AesGcmSecretProtector(new CredentialKeyRing(V0, null));
        var protector = new AesGcmSecretProtector(new CredentialKeyRing(V1, V0));

        var encryptedWithPreviousKey = previous.protect("old-application-password");

        assertThat(encryptedWithPreviousKey).startsWith("aesgcm:v1:");
        assertThat(protector.unprotect(encryptedWithPreviousKey.replace("aesgcm:v1:", "aesgcm:v0:")))
                .isEqualTo("old-application-password");
        assertThat(protector.requiresReprotect("aesgcm:v0:nonce:ciphertext")).isTrue();
    }

    @Test
    void rejectsMalformedOrUnknownCredentialPayloads() {
        var protector = new AesGcmSecretProtector(new CredentialKeyRing(V1, null));

        assertThatThrownBy(() -> protector.unprotect("aesgcm:v0:nonce:ciphertext"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> protector.unprotect("plaintext-password"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesToStartWithoutAnActiveCloudCredentialKey() {
        assertThatThrownBy(() -> new CredentialKeyRing(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAILFLOW_CREDENTIAL_KEY_V1");
    }

    private static String key(String value) {
        return Base64.getEncoder().encodeToString(value.substring(0, 32).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
