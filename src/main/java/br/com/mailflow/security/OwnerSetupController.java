package br.com.mailflow.security;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OwnerSetupController {

    private final AccountStore accounts;

    public OwnerSetupController(AccountStore accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/setup")
    String setupForm(Model model) {
        if (!accounts.isCloudSetupOpen()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("registrationForm", new RegistrationForm());
        return "auth/setup";
    }

    @PostMapping("/setup")
    String setup(@RequestParam(required = false) String setupToken,
                 @Valid @ModelAttribute RegistrationForm registrationForm,
                 BindingResult binding, RedirectAttributes redirect) {
        if (!accounts.isCloudSetupOpen()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (binding.hasErrors()) {
            return "auth/setup";
        }
        var token = setupToken == null ? new char[0] : setupToken.toCharArray();
        try {
            if (accounts.createInitialOwner(registrationForm, token)) {
                redirect.addFlashAttribute("success", "Sua conta foi criada. Entre para continuar.");
                return "redirect:/login";
            }
        } finally {
            java.util.Arrays.fill(token, '\0');
        }
        binding.reject("setup", "A configuração não pode ser concluída.");
        return "auth/setup";
    }
}
