package br.com.mailflow.settings.smtp;

public class DuplicateSmtpAccountException extends RuntimeException {

    public DuplicateSmtpAccountException() {
        super("Já existe uma conta SMTP com esse nome.");
    }
}

