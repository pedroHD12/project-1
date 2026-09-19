package br.com.mailflow.settings.smtp;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@Profile("cloud")
public class AesGcmSecretProtector implements SecretProtector {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final CredentialKeyRing keys;

    public AesGcmSecretProtector(CredentialKeyRing keys) {
        this.keys = keys;
    }

    @Override
    public String protect(String plainText) {
        try {
            var nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.activeKey(), new GCMParameterSpec(TAG_BITS, nonce));
            var encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return "aesgcm:v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)
                    + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Não foi possível proteger a credencial SMTP.", exception);
        }
    }

    @Override
    public String unprotect(String protectedText) {
        try {
            var parts = protectedText == null ? new String[0] : protectedText.split(":", -1);
            if (parts.length != 4 || !"aesgcm".equals(parts[0])) {
                throw new IllegalArgumentException("format");
            }
            var nonce = Base64.getUrlDecoder().decode(parts[2]);
            if (nonce.length != NONCE_BYTES) {
                throw new IllegalArgumentException("nonce");
            }
            var encrypted = Base64.getUrlDecoder().decode(parts[3]);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keys.keyForVersion(parts[1]), new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("A credencial SMTP não está em um formato protegido reconhecido.");
        }
    }

    @Override
    public boolean requiresReprotect(String protectedText) {
        return protectedText != null && protectedText.startsWith("aesgcm:v0:");
    }
}
