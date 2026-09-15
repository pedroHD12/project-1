package br.com.mailflow.settings.smtp;

import jakarta.mail.MessagingException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.UUID;

@Service
public class SmtpDiagnosticService {

    private final SmtpAccountService accountService;
    private long windowStart = System.nanoTime();
    private int operations;

    public SmtpDiagnosticService(SmtpAccountService accountService) {
        this.accountService = accountService;
    }

    public void testConnection(UUID accountId) {
        var account = accountService.get(accountId);
        checkLimit();
        try {
            createSender(account).testConnection();
        } catch (MessagingException | RuntimeException exception) {
            throw new SmtpDiagnosticException("Não foi possível conectar. Confira o serviço, a credencial de aplicativo e sua conexão.");
        }
    }

    public void sendTest(UUID accountId, String recipient) {
        var account = accountService.get(accountId);
        checkLimit();
        if (!account.isEnabled()) {
            throw new SmtpDiagnosticException("Ative a conta SMTP antes de enviar um teste.");
        }

        try {
            var sender = createSender(account);
            var mimeMessage = sender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            var from = StringUtils.hasText(account.getDefaultSender())
                    ? account.getDefaultSender()
                    : account.getUsername();
            if (!StringUtils.hasText(from)) {
                throw new SmtpDiagnosticException("Informe um remetente padrão ou usuário SMTP.");
            }

            helper.setFrom(from);
            helper.setTo(recipient.strip());
            helper.setSubject("MailFlow Local — teste de configuração SMTP");
            helper.setText("""
                    Este é um e-mail de teste solicitado manualmente no MailFlow Local.

                    Conta: %s
                    Servidor: %s:%d
                    Horário: %s

                    Nenhum agendamento automático foi criado.
                    """.formatted(
                    account.getName(),
                    account.getHost(),
                    account.getPort(),
                    ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            ));
            sender.send(mimeMessage);
        } catch (MessagingException | RuntimeException exception) {
            throw new SmtpDiagnosticException("Não foi possível confirmar o envio. Confira sua configuração antes de tentar novamente; a mensagem pode ter sido aceita pelo provedor.");
        }
    }

    private JavaMailSenderImpl createSender(SmtpAccount account) {
        if (account.getEncryptionMode() == EncryptionMode.NONE) {
            throw new SmtpDiagnosticException("Edite Meu e-mail e escolha uma conexão protegida.");
        }
        var sender = new JavaMailSenderImpl();
        sender.setHost(account.getHost());
        sender.setPort(account.getPort());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        if (StringUtils.hasText(account.getUsername())) {
            sender.setUsername(account.getUsername());
            sender.setPassword(accountService.revealSecret(account));
        }

        var properties = new Properties();
        properties.put("mail.smtp.auth", Boolean.toString(StringUtils.hasText(account.getUsername())));
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        properties.put("mail.smtp.starttls.enable", Boolean.toString(account.getEncryptionMode() == EncryptionMode.STARTTLS));
        properties.put("mail.smtp.starttls.required", Boolean.toString(account.getEncryptionMode() == EncryptionMode.STARTTLS));
        properties.put("mail.smtp.ssl.enable", Boolean.toString(account.getEncryptionMode() == EncryptionMode.TLS));
        properties.put("mail.smtp.ssl.checkserveridentity", "true");
        properties.put("mail.smtp.ssl.protocols", "TLSv1.3 TLSv1.2");
        sender.setJavaMailProperties(properties);
        return sender;
    }

    private synchronized void checkLimit() {
        long now = System.nanoTime();
        if (now - windowStart >= java.util.concurrent.TimeUnit.MINUTES.toNanos(1)) {
            windowStart = now;
            operations = 0;
        }
        if (++operations > 5) throw new SmtpDiagnosticException("Aguarde um minuto antes de testar novamente.");
    }
}
