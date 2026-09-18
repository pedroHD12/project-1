package br.com.mailflow.inbox;

import br.com.mailflow.settings.smtp.*;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImapReplyReaderTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={
        "Content-Type: multipart/mixed; boundary=missing\r\n\r\nprivate malformed content",
        "Content-Type: text/plain\r\nContent-Transfer-Encoding: base64\r\n\r\nA",
        "Content-Type: text/plain\r\nContent-Transfer-Encoding: unsupported\r\n\r\nprivate malformed content"
    })
    void malformedRelatedContentDoesNotBlockFollowingReply(String content) throws Exception {
        var malformed=new MimeMessage(Session.getInstance(new Properties()),new java.io.ByteArrayInputStream(
            ("From: recipient@example.test\r\nReferences: <123e4567-e89b-12d3-a456-426614174000@mailflow.local>\r\n"+content).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var valid=new MimeMessage(Session.getInstance(new Properties()));
        valid.setFrom("recipient@example.test"); valid.setHeader("References","<123e4567-e89b-12d3-a456-426614174000@mailflow.local>");
        valid.setText("Following reply"); valid.saveChanges();
        var store=mock(Store.class); var folder=mock(Folder.class,withSettings().extraInterfaces(UIDFolder.class)); var uids=(UIDFolder)folder;
        when(store.getFolder("INBOX")).thenReturn(folder); when(folder.isOpen()).thenReturn(true);
        when(folder.getMessageCount()).thenReturn(2); when(folder.getMessage(2)).thenReturn(valid);
        when(uids.getUIDValidity()).thenReturn(71L); when(uids.getUID(malformed)).thenReturn(100L); when(uids.getUID(valid)).thenReturn(101L);
        when(uids.getMessagesByUID(100,101)).thenReturn(new Message[]{malformed,valid});
        var account=new SmtpAccount(UUID.randomUUID(),"Gmail","smtp.gmail.com",587,"sender@gmail.com","protected",EncryptionMode.STARTTLS,"sender@gmail.com",true);

        var result=new ImapReplyReader(session->store).read(account,"synthetic-secret",71,99,h->true);

        assertThat(result.lastUid()).isEqualTo(101);
        assertThat(result.replies()).hasSize(2);
        assertThat(result.replies().getFirst().text()).contains("Abra no Gmail").doesNotContain("private malformed content");
        assertThat(result.replies().getLast().text()).isEqualTo("Following reply");
    }
    @Test void usesFixedTlsEndpointAndReadOnlyFolderWithoutExpunge() throws Exception {
        var store=mock(Store.class); var folder=mock(Folder.class,withSettings().extraInterfaces(UIDFolder.class));
        var uids=(UIDFolder)folder; var message=new MimeMessage(Session.getInstance(new Properties()));
        when(store.getFolder("INBOX")).thenReturn(folder); when(folder.isOpen()).thenReturn(true);
        when(folder.getMessageCount()).thenReturn(1); when(folder.getMessage(1)).thenReturn(message);
        when(uids.getUIDValidity()).thenReturn(71L); when(uids.getUID(message)).thenReturn(100L);
        var account=new SmtpAccount(UUID.randomUUID(),"Gmail","smtp.gmail.com",587,"sender@gmail.com","protected",EncryptionMode.STARTTLS,"sender@gmail.com",true);
        var reader=new ImapReplyReader(session->{
            assertThat(session.getProperty("mail.imaps.ssl.checkserveridentity")).isEqualTo("true");
            assertThat(session.getProperty("mail.imaps.peek")).isEqualTo("true");
            return store;
        });
        var result=reader.read(account,"synthetic-secret",71,100,h->false);
        assertThat(result.lastUid()).isEqualTo(100); assertThat(result.replies()).isEmpty();
        verify(store).connect("imap.gmail.com",993,"sender@gmail.com","synthetic-secret");
        verify(folder).open(Folder.READ_ONLY); verify(folder).close(false); verify(folder,never()).close(true);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void bodyTransportFailuresStillAbortTheBatch(boolean socketFailure) throws Exception {
        var store=mock(Store.class); var folder=mock(Folder.class,withSettings().extraInterfaces(UIDFolder.class)); var uids=(UIDFolder)folder;
        var socketError=new java.net.SocketTimeoutException("private transport details");
        var folderError=new FolderClosedException(folder,"private transport details");
        var message=new MimeMessage(Session.getInstance(new Properties())) {
            @Override public java.io.InputStream getInputStream() throws java.io.IOException,MessagingException {
                if(socketFailure) throw socketError;
                throw folderError;
            }
        };
        message.setFrom("recipient@example.test"); message.setHeader("References","<123e4567-e89b-12d3-a456-426614174000@mailflow.local>");
        message.setText("Body"); message.saveChanges();
        when(store.getFolder("INBOX")).thenReturn(folder); when(folder.isOpen()).thenReturn(true);
        when(folder.getMessageCount()).thenReturn(1); when(folder.getMessage(1)).thenReturn(message);
        when(uids.getUIDValidity()).thenReturn(71L); when(uids.getUID(message)).thenReturn(100L);
        when(uids.getMessagesByUID(100,100)).thenReturn(new Message[]{message});
        var account=new SmtpAccount(UUID.randomUUID(),"Gmail","smtp.gmail.com",587,"sender@gmail.com","protected",EncryptionMode.STARTTLS,"sender@gmail.com",true);

        assertThatThrownBy(()->new ImapReplyReader(session->store).read(account,"synthetic-secret",71,99,h->true))
            .isSameAs(socketFailure?socketError:folderError);
    }
    @Test void unrelatedMessageDoesNotReadItsBodyAndCursorResetsWithValidity() throws Exception {
        var store=mock(Store.class); var folder=mock(Folder.class,withSettings().extraInterfaces(UIDFolder.class)); var uids=(UIDFolder)folder;
        var message=new MimeMessage(Session.getInstance(new Properties())) {
            @Override public java.io.InputStream getInputStream() { throw new AssertionError("Unrelated body must not be downloaded"); }
        };
        message.setFrom("other@example.test"); message.setSubject("Unrelated");
        when(store.getFolder("INBOX")).thenReturn(folder); when(folder.isOpen()).thenReturn(true);
        when(folder.getMessageCount()).thenReturn(1); when(folder.getMessage(1)).thenReturn(message);
        when(uids.getUIDValidity()).thenReturn(72L); when(uids.getUID(message)).thenReturn(100L);
        when(uids.getMessagesByUID(1,100)).thenReturn(new Message[]{message});
        var account=new SmtpAccount(UUID.randomUUID(),"Gmail","smtp.gmail.com",587,"sender@gmail.com","protected",EncryptionMode.STARTTLS,"sender@gmail.com",true);
        var result=new ImapReplyReader(session->store).read(account,"synthetic-secret",71,900,h->false);
        assertThat(result.validity()).isEqualTo(72); assertThat(result.lastUid()).isEqualTo(100); assertThat(result.replies()).isEmpty();
    }
}
