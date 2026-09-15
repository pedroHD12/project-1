package br.com.mailflow.delivery;

public interface EmailGateway {
    enum Outcome { ACCEPTED, RETRYABLE, REJECTED, UNKNOWN }
    Outcome send(br.com.mailflow.settings.smtp.SmtpAccount account, EmailMessage message);
}
