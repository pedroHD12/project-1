package br.com.mailflow.delivery;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.web.util.HtmlUtils;
import java.util.Map;
import java.util.regex.Pattern;

public record MessageContent(String subject, String text, String html) {
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z][a-zA-Z0-9_.]*)\\s*}}");

    public static MessageContent render(String subject, String text, String html, String name, String email) {
        subject = value(subject); text = value(text); html = value(html);
        if (subject.isBlank() || subject.length()>998 || text.length()>50000 || html.length()>50000)
            throw new IllegalArgumentException("Informe um assunto (até 998 caracteres) e uma mensagem de até 50.000 caracteres por formato.");
        var values = Map.of("nome", name == null || name.isBlank() ? email : name, "email", email);
        subject = replace(subject, values, false);
        text = replace(text, values, false);
        html = replace(html, values, true);
        if (subject.length()>998 || subject.chars().anyMatch(c -> Character.isISOControl(c)))
            throw new IllegalArgumentException("O assunto não pode conter quebras de linha ou caracteres de controle.");
        // Formatting only: no attributes, URLs, embedded content, styles or tracking pixels.
        html = Jsoup.clean(html, new Safelist().addTags("p","br","strong","b","em","i","u","ul","ol","li","blockquote","h1","h2","h3","pre","code"));
        if(text.length()>50000 || html.length()>50000) throw new IllegalArgumentException("A mensagem personalizada excede 50.000 caracteres. Reduza o conteúdo.");
        if (text.isBlank() && Jsoup.parseBodyFragment(html).text().isBlank())
            throw new IllegalArgumentException("Escreva o conteúdo da mensagem.");
        if (text.isBlank()) text = Jsoup.parseBodyFragment(html).text();
        return new MessageContent(subject, text, html);
    }
    private static String value(String value) { return value == null ? "" : value.strip(); }
    private static String replace(String source, Map<String,String> values, boolean html) {
        var matcher = VARIABLE.matcher(source);
        var result = new StringBuilder();
        while (matcher.find()) {
            var replacement = values.get(matcher.group(1));
            if (replacement == null) throw new IllegalArgumentException("Use somente as variáveis {{nome}} e {{email}} nesta mensagem.");
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(html ? HtmlUtils.htmlEscape(replacement) : replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
