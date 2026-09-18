package br.com.mailflow.delivery;

import br.com.mailflow.settings.smtp.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Real TLS SMTP exchange. Trust changes are confined to this test JVM and restored afterward. */
class SecureGatewayTlsTest {
    @TempDir static Path temp;
    static SSLContext serverContext;
    static SSLContext previous;
    @BeforeAll static void tls() throws Exception {
        Path store=temp.resolve("synthetic.p12");
        var keytool=Path.of(System.getProperty("java.home"),"bin","keytool.exe");
        if(!Files.exists(keytool)) keytool=Path.of(System.getProperty("java.home"),"bin","keytool");
        var process=new ProcessBuilder(keytool.toString(),"-genkeypair","-alias","synthetic","-keyalg","RSA","-keysize","2048","-validity","2","-dname","CN=localhost","-ext","SAN=IP:127.0.0.1,DNS:localhost","-storetype","PKCS12","-keystore",store.toString(),"-storepass","synthetic-test-only","-noprompt")
            .redirectErrorStream(true).redirectOutput(temp.resolve("keytool.log").toFile()).start();
        assertThat(process.waitFor(20,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();
        var keys=KeyStore.getInstance("PKCS12");try(var input=Files.newInputStream(store)){keys.load(input,"synthetic-test-only".toCharArray());}
        var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"synthetic-test-only".toCharArray());
        var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(keys);
        serverContext=SSLContext.getInstance("TLS");serverContext.init(km.getKeyManagers(),tm.getTrustManagers(),null);
        previous=SSLContext.getDefault();SSLContext.setDefault(serverContext);
    }
    @AfterAll static void restore() {if(previous!=null)SSLContext.setDefault(previous);}
    private record Exchange(EmailGateway.Outcome outcome,String transcript) {}
    private Exchange exchange(boolean acknowledge,boolean rejectAuth) throws Exception {
        try(var server=(SSLServerSocket)serverContext.getServerSocketFactory().createServerSocket(0,1,InetAddress.getByName("127.0.0.1"));var executor=Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(7000);
            var result=executor.submit(() -> {
                var transcript=new StringBuilder();
                try(var socket=server.accept()) {
                    socket.setSoTimeout(7000);
                    var input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                    var output=new PrintWriter(socket.getOutputStream(),true,StandardCharsets.UTF_8);
                    output.print("220 synthetic TLS mail\r\n");output.flush();boolean data=false;String line;
                    while((line=input.readLine())!=null) {
                        transcript.append(line).append('\n');
                        if(data) {
                            if(!line.equals("."))continue;
                            if(!acknowledge) break;
                            output.print("250 Accepted\r\n");data=false;
                        } else if(line.startsWith("EHLO")) output.print(rejectAuth?"250-synthetic\r\n250 AUTH PLAIN LOGIN\r\n":"250 synthetic\r\n");
                        else if(line.startsWith("AUTH")) output.print("535 Synthetic rejection private-marker\r\n");
                        else if(line.startsWith("MAIL")||line.startsWith("RCPT"))output.print("250 OK\r\n");
                        else if(line.equals("DATA")){output.print("354 Continue\r\n");data=true;}
                        else if(line.equals("QUIT")){output.print("221 Bye\r\n");output.flush();break;}
                        else output.print("250 OK\r\n");
                        output.flush();
                    }
                } return transcript.toString();
            });
            var protector=new SecretProtector(){public String protect(String value){return "synthetic";}public String unprotect(String value){return "synthetic-only";}};
            var account=new SmtpAccount(UUID.randomUUID(),"Synthetic","127.0.0.1",server.getLocalPort(),rejectAuth?"sender@example.test":null,rejectAuth?"synthetic":null,EncryptionMode.TLS,"sender@example.test",true);
            var outcome=new SecureSmtpGateway(protector).send(account,new EmailMessage("sender@example.test",List.of("recipient@example.test"),"Synthetic subject","Private synthetic text","<p>Private synthetic text</p>","<123e4567-e89b-12d3-a456-426614174000@mailflow.local>"));
            return new Exchange(outcome,result.get(10,TimeUnit.SECONDS));
        }
    }
    @Test void acceptsRealTlsExchangeWithSingleRecipientAndMultipartBody() throws Exception {
        var result=exchange(true,false);
        assertThat(result.outcome()).isEqualTo(EmailGateway.Outcome.ACCEPTED);
        assertThat(result.transcript()).contains("RCPT TO:<recipient@example.test>","Subject: Synthetic subject","Private synthetic text","multipart/");
        assertThat(result.transcript()).contains("Message-ID: <123e4567-e89b-12d3-a456-426614174000@mailflow.local>");
        assertThat(result.transcript().lines().filter(line->line.startsWith("RCPT TO")).count()).isEqualTo(1);
    }
    @Test void lostAcknowledgementAfterDataIsUnknownNotRetryable() throws Exception {
        var result=exchange(false,false);
        assertThat(result.transcript()).contains("Private synthetic text");
        assertThat(result.outcome()).isEqualTo(EmailGateway.Outcome.UNKNOWN);
    }
    @Test void authenticationFailureNeverTransmitsContent() throws Exception {
        var result=exchange(true,true);
        assertThat(result.outcome()).isEqualTo(EmailGateway.Outcome.REJECTED);
        assertThat(result.transcript()).doesNotContain("DATA","MAIL FROM","Private synthetic text");
    }
}
