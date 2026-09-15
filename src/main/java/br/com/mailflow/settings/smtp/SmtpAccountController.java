package br.com.mailflow.settings.smtp;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/settings/smtp")
public class SmtpAccountController {

    private final SmtpAccountService accountService;
    private final SmtpDiagnosticService diagnosticService;
    private final org.springframework.validation.Validator validator;

    public SmtpAccountController(SmtpAccountService accountService, SmtpDiagnosticService diagnosticService, org.springframework.validation.Validator validator) {
        this.accountService = accountService;
        this.diagnosticService = diagnosticService;
        this.validator = validator;
    }

    @org.springframework.web.bind.annotation.InitBinder("smtpAccountForm")
    void bindAccount(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAllowedFields("name", "provider", "defaultSender", "host", "port", "username", "password", "encryptionMode", "enabled");
    }

    @ModelAttribute("encryptionModes")
    public EncryptionMode[] encryptionModes() {
        return new EncryptionMode[]{EncryptionMode.STARTTLS, EncryptionMode.TLS};
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("accounts", accountService.list());
        return "settings/smtp/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("smtpAccountForm", new SmtpAccountForm());
        model.addAttribute("formTitle", "Conectar meu e-mail");
        model.addAttribute("formAction", "/settings/smtp");
        return "settings/smtp/form";
    }

    @PostMapping
    public String create(
            @ModelAttribute SmtpAccountForm smtpAccountForm,
            BindingResult binding,
            Model model,
            RedirectAttributes redirect
    ) {
        accountService.prepareForm(smtpAccountForm, null);
        validator.validate(smtpAccountForm, binding);
        if (!binding.hasErrors()) {
            try {
                accountService.create(smtpAccountForm);
                redirect.addFlashAttribute("success", "E-mail salvo. Você já pode testar a conexão.");
                return "redirect:/settings/smtp";
            } catch (DuplicateSmtpAccountException exception) {
                binding.rejectValue("name", "duplicate", exception.getMessage());
            } catch (org.springframework.dao.DataIntegrityViolationException exception) {
                binding.rejectValue("name", "conflict", "Não foi possível salvar. Confira se já existe uma conta com esse nome.");
            }
        }

        model.addAttribute("formTitle", "Conectar meu e-mail");
        model.addAttribute("formAction", "/settings/smtp");
        return "settings/smtp/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("smtpAccountForm", SmtpAccountForm.from(accountService.get(id)));
        model.addAttribute("formTitle", "Editar meu e-mail");
        model.addAttribute("formAction", "/settings/smtp/" + id);
        return "settings/smtp/form";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable UUID id,
            @ModelAttribute SmtpAccountForm smtpAccountForm,
            BindingResult binding,
            Model model,
            RedirectAttributes redirect
    ) {
        accountService.prepareForm(smtpAccountForm, id);
        validator.validate(smtpAccountForm, binding);
        if (!binding.hasErrors()) {
            try {
                accountService.update(id, smtpAccountForm);
                redirect.addFlashAttribute("success", "E-mail atualizado.");
                return "redirect:/settings/smtp";
            } catch (DuplicateSmtpAccountException exception) {
                binding.rejectValue("name", "duplicate", exception.getMessage());
            } catch (org.springframework.dao.DataIntegrityViolationException exception) {
                binding.rejectValue("name", "conflict", "Não foi possível salvar. Confira se já existe uma conta com esse nome.");
            }
        }

        model.addAttribute("formTitle", "Editar meu e-mail");
        model.addAttribute("formAction", "/settings/smtp/" + id);
        return "settings/smtp/form";
    }

    @PostMapping("/{id}/diagnose")
    public String diagnose(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            diagnosticService.testConnection(id);
            redirect.addFlashAttribute("success", "Conexão realizada com sucesso. Nenhum e-mail foi enviado.");
        } catch (SmtpDiagnosticException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/settings/smtp";
    }

    @GetMapping("/{id}/test-email")
    public String testEmailForm(@PathVariable UUID id, Model model) {
        model.addAttribute("account", accountService.get(id));
        model.addAttribute("testEmailForm", new TestEmailForm());
        return "settings/smtp/test-email";
    }

    @PostMapping("/{id}/test-email")
    public String sendTestEmail(
            @PathVariable UUID id,
            @Valid @ModelAttribute TestEmailForm testEmailForm,
            BindingResult binding,
            Model model,
            RedirectAttributes redirect
    ) {
        if (binding.hasErrors()) {
            model.addAttribute("account", accountService.get(id));
            return "settings/smtp/test-email";
        }

        try {
            diagnosticService.sendTest(id, testEmailForm.getRecipient());
            redirect.addFlashAttribute("success", "E-mail de teste solicitado com sucesso.");
            return "redirect:/settings/smtp";
        } catch (SmtpDiagnosticException exception) {
            model.addAttribute("account", accountService.get(id));
            model.addAttribute("sendError", exception.getMessage());
            return "settings/smtp/test-email";
        }
    }
}
