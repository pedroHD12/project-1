package br.com.mailflow.settings.smtp;

public interface SecretProtector {

    String protect(String plainText);

    String unprotect(String protectedText);
}

