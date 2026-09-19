package br.com.mailflow.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudStartupValidatorTest {

    @Test
    void cloudRejectsDatabaseUrlWithoutCertificateVerification() {
        var runtime = new AppRuntimeProperties("cloud", "jdbc:postgresql://db.example.com:5432/mailflow?sslmode=require");

        assertThatThrownBy(() -> CloudStartupValidator.validate(runtime))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verificação TLS");
    }

    @Test
    void cloudAcceptsDatabaseUrlWithFullCertificateVerification() {
        var runtime = new AppRuntimeProperties("cloud", "jdbc:postgresql://db.example.com:5432/mailflow?sslmode=verify-full");

        assertThatCode(() -> CloudStartupValidator.validate(runtime)).doesNotThrowAnyException();
    }

    @Test
    void localDoesNotRequireRemoteDatabaseTlsSettings() {
        var runtime = new AppRuntimeProperties("local", "jdbc:postgresql://localhost:5432/mailflow");

        assertThatCode(() -> CloudStartupValidator.validate(runtime)).doesNotThrowAnyException();
    }

    @Test
    void cloudRejectsConflictingTlsModeParameters() {
        var runtime = new AppRuntimeProperties("cloud", "jdbc:postgresql://db.example.com/mailflow?sslmode=verify-full&sslmode=disable");

        assertThatThrownBy(() -> CloudStartupValidator.validate(runtime))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verificação TLS");
    }

    @Test
    void cloudProfileAndRuntimeModeMustAgree() {
        var runtime = new AppRuntimeProperties("local", "jdbc:postgresql://db.example.com/mailflow?sslmode=verify-full");

        assertThatThrownBy(() -> CloudStartupValidator.validateProfileMode(runtime, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_RUNTIME_MODE");
    }
}
