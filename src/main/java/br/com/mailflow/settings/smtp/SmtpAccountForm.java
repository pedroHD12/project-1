package br.com.mailflow.settings.smtp;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public class SmtpAccountForm {

    @NotBlank
    @Pattern(regexp = "AUTO|GOOGLE|CUSTOM", message = "Escolha o serviço onde seu e-mail foi criado.")
    private String provider = "AUTO";

    @NotBlank(message = "Informe um nome para identificar a conta.")
    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
    private String name;

    @NotBlank(message = "Informe o servidor SMTP.")
    @Pattern(regexp = "[a-zA-Z0-9.-]+", message = "Use apenas o endereço do servidor, sem https:// ou caminhos.")
    @Size(max = 255, message = "O servidor deve ter no máximo 255 caracteres.")
    private String host;

    @Min(value = 1, message = "A porta deve ser maior que zero.")
    @Max(value = 65535, message = "A porta deve ser menor que 65.536.")
    private int port = 587;

    @Size(max = 320, message = "O usuário deve ter no máximo 320 caracteres.")
    private String username;

    @Size(max = 500, message = "A senha ou token é muito grande.")
    private String password;

    @NotNull(message = "Escolha o tipo de segurança da conexão.")
    private EncryptionMode encryptionMode = EncryptionMode.STARTTLS;

    @NotBlank(message = "Informe seu e-mail de envio.")
    @Email(message = "Informe um e-mail válido.")
    @Size(max = 320, message = "O remetente deve ter no máximo 320 caracteres.")
    private String defaultSender;

    private boolean enabled = true;

    private boolean existingSecret;

    @AssertTrue(message = "Para este endereço, marque E-mail personalizado e escolha o serviço.")
    public boolean isProviderSupported() {
        return !"AUTO".equals(provider) || isGmailAddress();
    }

    @AssertTrue(message = "Use uma conexão protegida: STARTTLS ou TLS.")
    public boolean isConnectionProtected() {
        return encryptionMode != EncryptionMode.NONE;
    }

    public boolean isGmailAddress() {
        var email = defaultSender == null ? "" : defaultSender.strip().toLowerCase(java.util.Locale.ROOT);
        return email.endsWith("@gmail.com") || email.endsWith("@googlemail.com");
    }

    @AssertTrue(message = "Informe a senha/token para uma conta autenticada.")
    public boolean isAuthenticationComplete() {
        if (username == null || username.isBlank()) {
            return true;
        }
        return existingSecret || (password != null && !password.isBlank());
    }

    public static SmtpAccountForm from(SmtpAccount account) {
        var form = new SmtpAccountForm();
        form.name = account.getName();
        form.host = account.getHost();
        form.port = account.getPort();
        form.username = account.getUsername();
        form.encryptionMode = account.getEncryptionMode();
        form.defaultSender = account.getDefaultSender();
        form.enabled = account.isEnabled();
        form.existingSecret = account.hasProtectedSecret();
        form.provider = "smtp.gmail.com".equals(account.getHost()) ? "GOOGLE" : "CUSTOM";
        return form;
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public EncryptionMode getEncryptionMode() {
        return encryptionMode;
    }

    public void setEncryptionMode(EncryptionMode encryptionMode) {
        this.encryptionMode = encryptionMode;
    }

    public String getDefaultSender() {
        return defaultSender;
    }

    public void setDefaultSender(String defaultSender) {
        this.defaultSender = defaultSender;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isExistingSecret() {
        return existingSecret;
    }

    public void setExistingSecret(boolean existingSecret) {
        this.existingSecret = existingSecret;
    }
}
