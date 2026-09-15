package br.com.mailflow.settings.smtp;

import com.sun.jna.platform.win32.Crypt32Util;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class WindowsDpapiSecretProtector implements SecretProtector {

    private static final String PREFIX = "dpapi:";

    @Override
    public String protect(String plainText) {
        requireWindows();
        var encrypted = Crypt32Util.cryptProtectData(plainText.getBytes(StandardCharsets.UTF_8));
        return PREFIX + Base64.getEncoder().encodeToString(encrypted);
    }

    @Override
    public String unprotect(String protectedText) {
        requireWindows();
        if (protectedText == null || !protectedText.startsWith(PREFIX)) {
            throw new IllegalStateException("A credencial SMTP não está em um formato protegido reconhecido.");
        }
        var encrypted = Base64.getDecoder().decode(protectedText.substring(PREFIX.length()));
        var decrypted = Crypt32Util.cryptUnprotectData(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private void requireWindows() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            throw new IllegalStateException("A proteção DPAPI está disponível somente no Windows.");
        }
    }
}

