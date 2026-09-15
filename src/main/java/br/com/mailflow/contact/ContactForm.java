package br.com.mailflow.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class ContactForm {

    @NotBlank(message = "Informe o endereço de e-mail.")
    @Email(message = "Informe um endereço de e-mail válido.")
    @Size(max = 320, message = "O e-mail deve ter no máximo 320 caracteres.")
    private String email;

    @Size(max = 160, message = "O nome deve ter no máximo 160 caracteres.")
    private String displayName;

    @Size(max = 160, message = "A empresa deve ter no máximo 160 caracteres.")
    private String company;

    private LocalDate birthday;

    @Size(max = 4000, message = "As observações devem ter no máximo 4.000 caracteres.")
    private String notes;

    public static ContactForm from(Contact contact) {
        var form = new ContactForm();
        form.email = contact.getEmail();
        form.displayName = contact.getDisplayName();
        form.company = contact.getCompany();
        form.birthday = contact.getBirthday();
        form.notes = contact.getNotes();
        return form;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

