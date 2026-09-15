package br.com.mailflow.delivery;

import br.com.mailflow.settings.smtp.*;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class SecureGatewayTest {
    private final SecretProtector protector = new SecretProtector() {
        public String protect(String plain) { return "synthetic"; }
        public String unprotect(String secret) { return "synthetic-secret"; }
    };
    @Test void refusesTlsDowngradeWithoutSendingAuthenticationOrMessage() throws Exception {
        try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress()); var executor=Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(5000);
            var transcript=executor.submit(() -> {
                var result=new StringBuilder();
                try(var socket=server.accept()) {
                    socket.setSoTimeout(5000);
                    var input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
                    var output=new PrintWriter(socket.getOutputStream(),true,StandardCharsets.US_ASCII);
                    output.print("220 synthetic\r\n"); output.flush(); String line;
                    while((line=input.readLine())!=null) {
                        result.append(line).append('\n');
                        if(line.startsWith("EHLO")) output.print("250-synthetic\r\n250 AUTH LOGIN PLAIN\r\n");
                        else {output.print("221 Bye\r\n"); output.flush(); break;} output.flush();
                    }
                } return result.toString();
            });
            var account=new SmtpAccount(UUID.randomUUID(),"Synthetic",server.getInetAddress().getHostAddress(),server.getLocalPort(),"sender@example.test","synthetic",EncryptionMode.STARTTLS,"sender@example.test",true);
            var result=new SecureSmtpGateway(protector).send(account,new EmailMessage("sender@example.test",List.of("recipient@example.test"),"Subject","Body",null));
            assertThat(result).isEqualTo(EmailGateway.Outcome.RETRYABLE);
            assertThat(transcript.get(7,TimeUnit.SECONDS)).contains("EHLO").doesNotContain("AUTH","MAIL FROM","RCPT TO","DATA","synthetic-secret");
        }
    }
    @Test void rejectsMultiRecipientEnvelopeAndUnencryptedAccountBeforeNetwork() {
        var account=new SmtpAccount(UUID.randomUUID(),"Synthetic","smtp.example.test",587,null,null,EncryptionMode.NONE,"sender@example.test",true);
        var gateway=new SecureSmtpGateway(protector);
        assertThat(gateway.send(account,new EmailMessage("sender@example.test",List.of("a@example.test","b@example.test"),"Hi","Body",null))).isEqualTo(EmailGateway.Outcome.REJECTED);
        var tls=new SmtpAccount(UUID.randomUUID(),"Synthetic","smtp.example.test",587,null,null,EncryptionMode.STARTTLS,"sender@example.test",true);
        assertThat(gateway.send(tls,new EmailMessage("sender@example.test",List.of("a@example.test","b@example.test"),"Hi","Body",null))).isEqualTo(EmailGateway.Outcome.REJECTED);
    }
}
