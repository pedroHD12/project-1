package br.com.mailflow.delivery;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MessageContentTest {
    @Test void stripsActiveHtmlAndEscapesPersonalization() {
        var content = MessageContent.render("Olá {{nome}}", "Para {{email}}", "<p onclick='evil()'>{{nome}}</p><script>evil()</script><img src='https://tracker.test/x'><a href='javascript:evil()'>link</a>", "<img src=x>", "person@example.test");
        assertThat(content.html()).contains("&lt;img src=x&gt;").doesNotContain("<script", "onclick", "<img", "href", "https://");
        assertThat(content.text()).isEqualTo("Para person@example.test");
    }
    @Test void rejectsHeaderInjectionAndUnknownVariables() {
        assertThatThrownBy(() -> MessageContent.render("Subject\r\nBcc: thief@example.test", "Body", "", "Name", "person@example.test")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MessageContent.render("Hello", "{{secret}}", "", "Name", "person@example.test")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MessageContent.render("{{nome}}", "Body", "", "Name\nBcc: bad", "person@example.test")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void requiresVisibleContentAndBoundsPayload() {
        assertThatThrownBy(() -> MessageContent.render("Hi", "", "<script>bad</script>", "Name", "person@example.test")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MessageContent.render("Hi", "x".repeat(50001), "", "Name", "person@example.test")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void schedulePreservesLocalTimeAndRejectsAmbiguousTimes() {
        var form = new DispatchForm(); form.setMode("WEEKLY"); form.setOccurrences(3);
        form.setScheduledAt("2026-10-25T09:00"); form.setTimezone("America/New_York");
        assertThat(form.dates(java.time.Instant.parse("2026-09-10T00:00:00Z"))).containsExactly(
            java.time.Instant.parse("2026-10-25T13:00:00Z"),java.time.Instant.parse("2026-11-01T14:00:00Z"),java.time.Instant.parse("2026-11-08T14:00:00Z"));
        form.setScheduledAt("2026-11-01T01:30");
        assertThatThrownBy(() -> form.dates(java.time.Instant.parse("2026-09-10T00:00:00Z"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void immediateMessageAlsoRejectsInvalidTimezone() {
        var form=new DispatchForm();form.setTimezone("Invalid/Zone");
        assertThatThrownBy(() -> form.dates(java.time.Instant.now())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void personalizationCannotExpandPayloadBeyondLimit() {
        assertThatThrownBy(() -> MessageContent.render("Hi","{{email}}".repeat(5000),"","Name","person@example.test")).isInstanceOf(IllegalArgumentException.class);
    }
}
