package br.com.mailflow.settings.smtp;

public enum EncryptionMode {
    NONE("Sem criptografia"),
    STARTTLS("STARTTLS"),
    TLS("SSL/TLS");

    private final String label;

    EncryptionMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

