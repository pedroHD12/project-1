package br.com.mailflow.settings.smtp;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpTransportSecurityTest {
    @Test void doesNotAuthenticateWhenServerOmitsStartTls() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(5000);
            var commands = executor.submit(() -> {
                var transcript = new StringBuilder();
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    var output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.US_ASCII);
                    output.print("220 local synthetic test\r\n"); output.flush();
                    String line;
                    while ((line = input.readLine()) != null) {
                        transcript.append(line).append('\n');
                        if (line.startsWith("EHLO")) output.print("250-local\r\n250 AUTH LOGIN PLAIN\r\n");
                        else if (line.startsWith("AUTH")) output.print("535 Authentication rejected\r\n");
                        else { output.print("221 Bye\r\n"); output.flush(); break; }
                        output.flush();
                    }
                }
                return transcript.toString();
            });
            var accounts = mock(SmtpAccountService.class);
            var account = new SmtpAccount(UUID.randomUUID(), "Synthetic", server.getInetAddress().getHostAddress(),
                    server.getLocalPort(), "test@example.test", "dpapi:test", EncryptionMode.STARTTLS, "test@example.test", true);
            var id = UUID.randomUUID();
            when(accounts.get(id)).thenReturn(account);
            when(accounts.revealSecret(account)).thenReturn("synthetic-secret");
            assertThatThrownBy(() -> new SmtpDiagnosticService(accounts).testConnection(id))
                .isInstanceOf(SmtpDiagnosticException.class);
            assertThat(commands.get(7, TimeUnit.SECONDS)).contains("EHLO").doesNotContain("AUTH");
        }
    }

    @Test void doesNotExposeRemoteErrorTextOrCredentials() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(5000);
            var response = executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.getOutputStream().write("554 private-marker user@example.test secret-value\r\n".getBytes(StandardCharsets.US_ASCII));
                }
                return true;
            });
            var accounts = mock(SmtpAccountService.class);
            var account = new SmtpAccount(UUID.randomUUID(), "Synthetic", server.getInetAddress().getHostAddress(),
                    server.getLocalPort(), null, null, EncryptionMode.STARTTLS, "test@example.test", true);
            var id = UUID.randomUUID();
            when(accounts.get(id)).thenReturn(account);
            assertThatThrownBy(() -> new SmtpDiagnosticService(accounts).testConnection(id))
                .isInstanceOf(SmtpDiagnosticException.class).hasMessageNotContaining("private-marker")
                .hasMessageNotContaining("secret-value").hasMessageNotContaining("user@example.test");
            assertThat(response.get(7, TimeUnit.SECONDS)).isTrue();
        }
    }
}
