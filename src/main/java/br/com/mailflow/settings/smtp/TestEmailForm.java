package br.com.mailflow.settings.smtp;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TestEmailForm {

    @NotBlank(message = "Informe o destinatário do teste.")
    @Email(message = "Informe um destinatário válido.")
    @Size(max = 320, message = "O endereço deve ter no máximo 320 caracteres.")
    private String recipient;

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }
}

