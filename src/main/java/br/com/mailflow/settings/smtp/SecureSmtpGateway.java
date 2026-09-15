package br.com.mailflow.settings.smtp;

import br.com.mailflow.delivery.*;
import jakarta.mail.*;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import java.util.Properties;
import java.nio.charset.StandardCharsets;

@Component
public class SecureSmtpGateway implements EmailGateway {
    private final SecretProtector protector;
    public SecureSmtpGateway(SecretProtector protector) { this.protector=protector; }

    @Override
    public Outcome send(SmtpAccount account,EmailMessage email) {
        Transport transport=null;
        boolean transmissionStarted=false;
        try {
            if(!account.isEnabled() || account.getEncryptionMode()==EncryptionMode.NONE || email.recipients().size()!=1
                || !java.util.Objects.equals(email.from(),account.getDefaultSender())) return Outcome.REJECTED;
            DispatchService.address(email.from()); DispatchService.address(email.recipients().getFirst());
            if(email.subject()==null || email.subject().isBlank() || email.subject().length()>998 || email.subject().chars().anyMatch(Character::isISOControl)) return Outcome.REJECTED;
            var props=new Properties();
            props.setProperty("mail.smtp.auth",Boolean.toString(account.getUsername()!=null));
            props.setProperty("mail.smtp.starttls.enable",Boolean.toString(account.getEncryptionMode()==EncryptionMode.STARTTLS));
            props.setProperty("mail.smtp.starttls.required",Boolean.toString(account.getEncryptionMode()==EncryptionMode.STARTTLS));
            props.setProperty("mail.smtp.ssl.enable",Boolean.toString(account.getEncryptionMode()==EncryptionMode.TLS));
            props.setProperty("mail.smtp.ssl.checkserveridentity","true");
            props.setProperty("mail.smtp.ssl.protocols","TLSv1.3 TLSv1.2");
            props.setProperty("mail.smtp.connectiontimeout","10000"); props.setProperty("mail.smtp.timeout","10000"); props.setProperty("mail.smtp.writetimeout","10000");
            props.setProperty("mail.smtp.sendpartial","false"); props.setProperty("mail.smtp.quitwait","false");
            var session=Session.getInstance(props); session.setDebug(false);
            var message=new jakarta.mail.internet.MimeMessage(session);
            boolean hasHtml=email.htmlBody()!=null && !email.htmlBody().isBlank();
            var helper=new MimeMessageHelper(message,hasHtml,StandardCharsets.UTF_8.name());
            helper.setFrom(email.from()); helper.setTo(email.recipients().getFirst()); helper.setSubject(email.subject());
            if(hasHtml) helper.setText(email.textBody(),email.htmlBody()); else helper.setText(email.textBody());
            message.saveChanges();
            String secret=account.getUsername()==null || !account.hasProtectedSecret()?null:protector.unprotect(account.getSecretReference());
            transport=session.getTransport("smtp");
            transport.connect(account.getHost(),account.getPort(),account.getUsername(),secret);
            // After this boundary the remote server might have accepted DATA even if the acknowledgement is lost.
            transmissionStarted=true;
            transport.sendMessage(message,message.getAllRecipients());
            return Outcome.ACCEPTED;
        } catch(AuthenticationFailedException ex) {
            return Outcome.REJECTED;
        } catch(MessagingException ex) {
            return transmissionStarted?Outcome.UNKNOWN:Outcome.RETRYABLE;
        } catch(RuntimeException ex) {
            return transmissionStarted?Outcome.UNKNOWN:Outcome.REJECTED;
        } finally {
            if(transport!=null) try { transport.close(); } catch(MessagingException ignored) { /* QUIT cannot undo an acknowledged send. */ }
        }
    }
}
