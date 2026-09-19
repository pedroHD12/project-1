package br.com.mailflow.settings.smtp;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
@Profile("cloud")
public class CredentialKeyRing {

    private final SecretKey v1;
    private final SecretKey v0;

    @Autowired
    public CredentialKeyRing(CredentialKeyProperties properties) {
        this(properties.keyV1(), properties.keyV0());
    }

    CredentialKeyRing(String keyV1, String keyV0) {
        this.v1 = decodeRequired(keyV1, "MAILFLOW_CREDENTIAL_KEY_V1");
        this.v0 = decodeOptional(keyV0, "MAILFLOW_CREDENTIAL_KEY_V0");
    }

    SecretKey activeKey() {
        return v1;
    }

    SecretKey keyForVersion(String version) {
        if ("v1".equals(version)) {
            return v1;
        }
        if ("v0".equals(version) && v0 != null) {
            return v0;
        }
        throw new IllegalStateException("A credencial SMTP usa uma chave indisponível.");
    }

    private SecretKey decodeRequired(String encoded, String name) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(name + " é obrigatório no modo cloud.");
        }
        return decode(encoded, name);
    }

    private SecretKey decodeOptional(String encoded, String name) {
        return encoded == null || encoded.isBlank() ? null : decode(encoded, name);
    }

    private SecretKey decode(String encoded, String name) {
        try {
            var decoded = Base64.getDecoder().decode(encoded.strip());
            if (decoded.length != 32) {
                throw new IllegalArgumentException("length");
            }
            return new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(name + " deve conter uma chave Base64 de 32 bytes.");
        }
    }
}
