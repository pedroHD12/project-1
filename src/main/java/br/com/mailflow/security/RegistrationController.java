package br.com.mailflow.security;

import jakarta.validation.Valid;
import br.com.mailflow.config.AppRuntimeProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class RegistrationController {
    private final AccountStore accounts;
    private final AppRuntimeProperties runtime;
    public RegistrationController(AccountStore accounts, AppRuntimeProperties runtime) {
        this.accounts = accounts;
        this.runtime = runtime;
    }
    @GetMapping("/login") String login(Model model) {
        model.addAttribute("registrationOpen", accounts.isRegistrationOpen());
        return "auth/login";
    }
    @GetMapping("/register") String registerForm(Model model) {
        rejectCloudRegistration();
        if (!accounts.isRegistrationOpen()) return "redirect:/login";
        model.addAttribute("registrationForm", new RegistrationForm());
        return "auth/register";
    }
    @PostMapping("/register")
    String register(@Valid @ModelAttribute RegistrationForm registrationForm, BindingResult binding,
                    RedirectAttributes redirect) {
        rejectCloudRegistration();
        if (!accounts.isRegistrationOpen()) return "redirect:/login";
        if (binding.hasErrors()) return "auth/register";
        if (accounts.register(registrationForm))
            redirect.addFlashAttribute("success", "Sua conta foi criada. Entre para continuar.");
        return "redirect:/login";
    }
    private void rejectCloudRegistration() {
        if (runtime.isCloud()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}

