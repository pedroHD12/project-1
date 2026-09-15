package br.com.mailflow.template;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EmailTemplateForm {

    @NotBlank(message = "Informe um nome para o template.")
    @Size(max = 160, message = "O nome deve ter no máximo 160 caracteres.")
    private String name;

    @NotBlank(message = "Informe o assunto do e-mail.")
    @Size(max = 998, message = "O assunto deve ter no máximo 998 caracteres.")
    private String subject;

    @Size(max = 100_000, message = "O conteúdo em texto é muito grande.")
    private String bodyText;

    @Size(max = 300_000, message = "O conteúdo HTML é muito grande.")
    private String bodyHtml;

    @AssertTrue(message = "Informe o conteúdo em texto ou HTML.")
    public boolean isContentPresent() {
        return (bodyText != null && !bodyText.isBlank()) || (bodyHtml != null && !bodyHtml.isBlank());
    }

    public static EmailTemplateForm from(EmailTemplate template) {
        var form = new EmailTemplateForm();
        form.name = template.getName();
        form.subject = template.getSubject();
        form.bodyText = template.getBodyText();
        form.bodyHtml = template.getBodyHtml();
        return form;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBodyText() {
        return bodyText;
    }

    public void setBodyText(String bodyText) {
        this.bodyText = bodyText;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml;
    }
}

