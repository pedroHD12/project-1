package br.com.mailflow.delivery;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public class DispatchForm {
    @NotNull private UUID accountId;
    @Size(min=1,max=20) @NotNull private List<UUID> contactIds = new ArrayList<>();
    @NotBlank @Size(max=998) private String subject;
    @Size(max=50000) private String bodyText;
    @Size(max=50000) private String bodyHtml;
    @NotNull @Pattern(regexp="NOW|ONCE|DAILY|WEEKLY") private String mode = "NOW";
    @Size(max=30) private String scheduledAt;
    @NotBlank @Size(max=80) private String timezone = "America/Sao_Paulo";
    @Min(1) @Max(30) private int occurrences = 1;

    public List<Instant> dates(Instant now) {
        if (!Set.of("NOW","ONCE","DAILY","WEEKLY").contains(mode == null ? "" : mode))
            throw new IllegalArgumentException("Escolha quando enviar.");
        try {
            var zone=ZoneId.of(timezone);
            if ("NOW".equals(mode)) return List.of(now);
            var first=LocalDateTime.parse(scheduledAt);
            int count = Set.of("DAILY","WEEKLY").contains(mode) ? occurrences : 1;
            if (count<1 || count>30) throw new IllegalArgumentException("Escolha de 1 a 30 ocorrências.");
            var dates=new ArrayList<Instant>();
            for(int i=0;i<count;i++) {
                var local = "WEEKLY".equals(mode) ? first.plusWeeks(i) : first.plusDays(i);
                var offsets=zone.getRules().getValidOffsets(local);
                if(offsets.size()!=1) throw new IllegalArgumentException("Esse horário é ambíguo ou não existe no fuso escolhido. Escolha outro horário.");
                dates.add(local.toInstant(offsets.getFirst()));
            }
            if(!dates.getFirst().isAfter(now) || dates.getLast().isAfter(now.plus(Duration.ofDays(366))))
                throw new IllegalArgumentException("Agende um horário futuro, dentro dos próximos 366 dias.");
            return List.copyOf(dates);
        } catch(DateTimeException | NullPointerException ex) {
            throw new IllegalArgumentException("Confira a data, o horário e o fuso do agendamento.");
        }
    }
    public UUID getAccountId(){return accountId;} public void setAccountId(UUID v){accountId=v;}
    public List<UUID> getContactIds(){return contactIds;} public void setContactIds(List<UUID> v){contactIds=v;}
    public String getSubject(){return subject;} public void setSubject(String v){subject=v;}
    public String getBodyText(){return bodyText;} public void setBodyText(String v){bodyText=v;}
    public String getBodyHtml(){return bodyHtml;} public void setBodyHtml(String v){bodyHtml=v;}
    public String getMode(){return mode;} public void setMode(String v){mode=v;}
    public String getScheduledAt(){return scheduledAt;} public void setScheduledAt(String v){scheduledAt=v;}
    public String getTimezone(){return timezone;} public void setTimezone(String v){timezone=v;}
    public int getOccurrences(){return occurrences;} public void setOccurrences(int v){occurrences=v;}
}
