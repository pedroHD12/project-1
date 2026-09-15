package br.com.mailflow.security;

import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public class RegistrationForm {
    @NotBlank(message = "Informe seu nome.") @Size(max = 160)
    private String name;
    @NotBlank(message = "Informe seu e-mail.") @Email(message = "Informe um e-mail válido.") @Size(max = 320)
    private String email;
    @NotBlank(message = "Crie uma senha.")
    @Size(min = 12, max = 72, message = "Use uma senha com 12 a 72 caracteres.")
    private String password;
    @NotBlank(message = "Repita sua senha.") @Size(max = 72)
    private String confirmPassword;

    @AssertTrue(message = "As senhas precisam ser iguais.")
    public boolean isPasswordConfirmed() { return password != null && password.equals(confirmPassword); }

    @AssertTrue(message = "Use uma frase-senha menos comum, com até 72 bytes UTF-8.")
    public boolean isPasswordSafe() {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72
            && !Set.of("123456789012", "password1234", "qwerty123456", "abcdefghijkl", "111111111111")
                .contains(password.toLowerCase(Locale.ROOT));
    }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email = v == null ? null : v.strip(); }
    public String getPassword() { return password; }
    public void setPassword(String v) { password = v; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String v) { confirmPassword = v; }
}

