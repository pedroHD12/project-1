package br.com.mailflow.settings.smtp;

public class SmtpDiagnosticException extends RuntimeException {

    public SmtpDiagnosticException(String message, Throwable cause) {
        super(message, cause);
    }

    public SmtpDiagnosticException(String message) {
        super(message);
    }
}

