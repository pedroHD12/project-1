package br.com.mailflow.security;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RegistrationController {
    private final AccountStore accounts;
    public RegistrationController(AccountStore accounts) { this.accounts = accounts; }
    @GetMapping("/login") String login(Model model) {
        model.addAttribute("registrationOpen", accounts.isRegistrationOpen());
        return "auth/login";
    }
    @GetMapping("/register") String registerForm(Model model) {
        if (!accounts.isRegistrationOpen()) return "redirect:/login";
        model.addAttribute("registrationForm", new RegistrationForm());
        return "auth/register";
    }
    @PostMapping("/register")
    String register(@Valid @ModelAttribute RegistrationForm registrationForm, BindingResult binding,
                    RedirectAttributes redirect) {
        if (!accounts.isRegistrationOpen()) return "redirect:/login";
        if (binding.hasErrors()) return "auth/register";
        if (accounts.register(registrationForm))
            redirect.addFlashAttribute("success", "Sua conta foi criada. Entre para continuar.");
        return "redirect:/login";
    }
}

