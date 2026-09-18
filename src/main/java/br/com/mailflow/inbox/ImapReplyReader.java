package br.com.mailflow.inbox;

import br.com.mailflow.settings.smtp.EncryptionMode;
import br.com.mailflow.settings.smtp.SmtpAccount;
import jakarta.mail.*;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.ParseException;
import org.eclipse.angus.mail.util.DecodingException;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;

@Component
public class ImapReplyReader {
    public record Reply(ReplyHeader header,String text) {}
    public record Batch(long validity,long lastUid,List<Reply> replies) {
        public Batch { replies=List.copyOf(replies); }
    }
    @FunctionalInterface interface StoreFactory { Store create(Session session) throws MessagingException; }
    private final StoreFactory stores;
    public ImapReplyReader() { this(session->session.getStore("imaps")); }
    ImapReplyReader(StoreFactory stores) { this.stores=stores; }
    public static boolean supports(SmtpAccount account) {
        return account.isEnabled() && "smtp.gmail.com".equalsIgnoreCase(account.getHost())
            && account.getEncryptionMode()!=EncryptionMode.NONE && account.hasProtectedSecret()
            && account.getUsername()!=null && account.getUsername().equalsIgnoreCase(account.getDefaultSender());
    }
    public Batch read(SmtpAccount account,String secret,long knownValidity,long lastUid,Predicate<ReplyHeader> related) throws MessagingException,IOException {
        if(!supports(account) || secret==null || secret.isBlank()) throw new IllegalArgumentException("Configure uma conta Gmail com senha de aplicativo.");
        var properties=new Properties();
        properties.setProperty("mail.imaps.ssl.checkserveridentity","true"); properties.setProperty("mail.imaps.ssl.protocols","TLSv1.3 TLSv1.2");
        properties.setProperty("mail.imaps.connectiontimeout","10000"); properties.setProperty("mail.imaps.timeout","10000"); properties.setProperty("mail.imaps.writetimeout","10000");
        properties.setProperty("mail.imaps.peek","true"); properties.setProperty("mail.imaps.fetchsize","16384");
        properties.setProperty("mail.imaps.auth.mechanisms","PLAIN LOGIN");
        var session=Session.getInstance(properties); session.setDebug(false);
        try(var store=stores.create(session)) {
            store.connect("imap.gmail.com",993,account.getUsername(),secret);
            var folder=store.getFolder("INBOX");
            try {
                folder.open(Folder.READ_ONLY);
                if(!(folder instanceof UIDFolder uids)) throw new MessagingException("UID indisponível.");
                long validity=uids.getUIDValidity(); int count=folder.getMessageCount();
                if(count==0) return new Batch(validity,knownValidity==validity?lastUid:0,List.of());
                long latest=uids.getUID(folder.getMessage(count));
                long start=knownValidity==validity?lastUid+1:Math.max(1,latest-199);
                if(start>latest) return new Batch(validity,lastUid,List.of());
                long end=Math.min(latest,start+199);
                var messages=Arrays.stream(uids.getMessagesByUID(start,end)).filter(Objects::nonNull).toArray(Message[]::new);
                var profile=new FetchProfile(); profile.add(FetchProfile.Item.ENVELOPE); profile.add(FetchProfile.Item.CONTENT_INFO); profile.add(UIDFolder.FetchProfileItem.UID);
                profile.add("Message-ID"); profile.add("In-Reply-To"); profile.add("References"); folder.fetch(messages,profile);
                var replies=new ArrayList<Reply>(); long deadline=System.nanoTime()+java.time.Duration.ofSeconds(45).toNanos();
                long cursor=start-1; boolean complete=true; int bodies=0;
                for(var message:messages) {
                    if(System.nanoTime()>deadline) { complete=false; break; }
                    long uid=uids.getUID(message); var header=ReplyHeader.from(message,uid);
                    if(!header.from().isBlank() && !header.references().isEmpty() && related.test(header)) {
                        String text;
                        try { text=bodies++<20?text(message):"Texto não importado neste lote. Abra a mensagem no Gmail para ler o conteúdo completo."; }
                        catch(ParseException | DecodingException ex) {
                            text="Não foi possível interpretar o conteúdo desta resposta. Abra no Gmail para ler a mensagem.";
                        }
                        replies.add(new Reply(header,text));
                    }
                    cursor=Math.max(cursor,uid);
                }
                return new Batch(validity,complete?end:cursor,replies);
            } finally {
                if(folder.isOpen()) folder.close(false);
            }
        }
    }
    static String text(Part part) throws MessagingException,IOException {
        if(part.getSize()>262144) return "Mensagem maior que o limite de leitura. Abra no Gmail para ler o conteúdo completo.";
        return text(part,0,new int[]{0});
    }
    private static String text(Part part,int depth,int[] parts) throws MessagingException,IOException {
        if(depth>5 || ++parts[0]>20 || Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName()!=null) return "";
        if(part.isMimeType("text/plain") || part.isMimeType("text/html")) {
            requireSupportedEncoding(part);
            String name=new ContentType(part.getContentType()).getParameter("charset"); Charset charset=StandardCharsets.UTF_8;
            if(name!=null) try { charset=Charset.forName(name); } catch(IllegalArgumentException ignored) { /* Safe default for unknown encodings. */ }
            String value;
            try(var input=part.getInputStream()) { value=new String(input.readNBytes(50000),charset); }
            if(part.isMimeType("text/html")) { var document=Jsoup.parse(value); document.select("script,style,iframe,object").remove(); value=document.text(); }
            return value.substring(0,Math.min(value.length(),50000));
        }
        if(part.isMimeType("multipart/*")) {
            requireSupportedEncoding(part);
            var multipart=(Multipart)part.getContent(); String fallback="";
            for(int i=0;i<Math.min(multipart.getCount(),20);i++) {
                var child=multipart.getBodyPart(i); var value=text(child,depth+1,parts);
                if(!value.isBlank()) { if(child.isMimeType("text/plain") || !part.isMimeType("multipart/alternative")) return value; fallback=value; }
            }
            return fallback;
        }
        return "";
    }
    private static void requireSupportedEncoding(Part part) throws MessagingException,DecodingException {
        var headers=part.getHeader("Content-Transfer-Encoding");
        if(headers==null) return;
        for(var header:headers) switch(header.trim().toLowerCase(Locale.ROOT)) {
            case "7bit","8bit","binary","base64","quoted-printable" -> { }
            default -> throw new DecodingException("Unsupported content-transfer encoding.");
        }
    }
}
