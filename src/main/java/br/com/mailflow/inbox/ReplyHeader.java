package br.com.mailflow.inbox;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

public record ReplyHeader(long uid,String from,String subject,Instant receivedAt,List<UUID> references,String messageId) {
    private static final Pattern MAILFLOW_ID=Pattern.compile("<([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})@mailflow\\.local>");
    public ReplyHeader { references=List.copyOf(references); }
    public static ReplyHeader from(Message message,long uid) throws MessagingException {
        var addresses=message.getFrom();
        String sender=addresses!=null && addresses.length==1 && addresses[0] instanceof InternetAddress address?clean(address.getAddress(),320):"";
        var references=new LinkedHashSet<UUID>();
        for(var name:List.of("In-Reply-To","References")) {
            var headers=message.getHeader(name);
            if(headers==null) continue;
            for(var value:headers) {
                var matcher=MAILFLOW_ID.matcher(value.substring(0,Math.min(value.length(),8192)));
                while(matcher.find() && references.size()<20) references.add(UUID.fromString(matcher.group(1)));
            }
        }
        var dates=message.getReceivedDate(); if(dates==null) dates=message.getSentDate();
        var ids=message.getHeader("Message-ID");
        return new ReplyHeader(uid,sender,clean(message.getSubject(),998),dates==null?Instant.now():dates.toInstant(),new ArrayList<>(references),ids==null || ids.length!=1?"":clean(ids[0],998));
    }
    private static String clean(String value,int max) {
        if(value==null) return "";
        return value.substring(0,Math.min(value.length(),max)).replaceAll("[\\p{Cntrl}]"," ").strip();
    }
}
