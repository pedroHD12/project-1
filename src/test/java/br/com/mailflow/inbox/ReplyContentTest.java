package br.com.mailflow.inbox;

import jakarta.mail.Session;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ReplyContentTest {
    private MimeMessage message() { return new MimeMessage(Session.getInstance(new Properties())); }
    @Test void matchesOnlyMailflowIdsAndRejectsAmbiguousSenders() throws Exception {
        var message=message(); message.setFrom("recipient@example.test"); message.setSubject("Re: olá");
        message.setHeader("References","<123e4567-e89b-12d3-a456-426614174000@other.test> <123e4567-e89b-12d3-a456-426614174000@mailflow.local>");
        var header=ReplyHeader.from(message,42);
        assertThat(header.references()).containsExactly(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        assertThat(header.from()).isEqualTo("recipient@example.test");
        message.setHeader("From","one@example.test, two@example.test");
        assertThat(ReplyHeader.from(message,42).from()).isEmpty();
    }
    @Test void convertsHtmlToTextWithoutScriptsOrExternalContent() throws Exception {
        var message=message(); message.setContent("<p>Olá, recebi.</p><script>hostile()</script><img src='https://external.test/pixel'>","text/html; charset=UTF-8"); message.saveChanges();
        assertThat(ImapReplyReader.text(message)).isEqualTo("Olá, recebi.");
    }
    @Test void ignoresAttachmentsAndBoundsMessageContent() throws Exception {
        var multipart=new MimeMultipart(); var body=new MimeBodyPart(); body.setText("Resposta privada","UTF-8"); multipart.addBodyPart(body);
        var attachment=new MimeBodyPart(); attachment.setText("Não importar esse arquivo"); attachment.setFileName("secret.txt"); multipart.addBodyPart(attachment);
        var message=message(); message.setContent(multipart); message.saveChanges();
        assertThat(ImapReplyReader.text(message)).isEqualTo("Resposta privada");
        message.setText("x".repeat(300000)); message.saveChanges();
        assertThat(ImapReplyReader.text(message)).hasSizeLessThanOrEqualTo(50000);
    }
}
