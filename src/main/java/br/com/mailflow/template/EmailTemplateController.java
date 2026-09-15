package br.com.mailflow.template;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/templates")
public class EmailTemplateController {

    private final EmailTemplateService service;

    public EmailTemplateController(EmailTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("templates", service.list());
        return "email-templates/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("emailTemplateForm", new EmailTemplateForm());
        model.addAttribute("formTitle", "Novo modelo");
        model.addAttribute("formAction", "/templates");
        return "email-templates/form";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute EmailTemplateForm emailTemplateForm,
            BindingResult binding,
            Model model,
            RedirectAttributes redirect
    ) {
        if (binding.hasErrors()) {
            model.addAttribute("formTitle", "Novo modelo");
            model.addAttribute("formAction", "/templates");
            return "email-templates/form";
        }

        service.create(emailTemplateForm);
        redirect.addFlashAttribute("success", "Modelo criado com sucesso.");
        return "redirect:/templates";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("emailTemplateForm", EmailTemplateForm.from(service.get(id)));
        model.addAttribute("formTitle", "Editar modelo");
        model.addAttribute("formAction", "/templates/" + id);
        return "email-templates/form";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute EmailTemplateForm emailTemplateForm,
            BindingResult binding,
            Model model,
            RedirectAttributes redirect
    ) {
        if (binding.hasErrors()) {
            model.addAttribute("formTitle", "Editar modelo");
            model.addAttribute("formAction", "/templates/" + id);
            return "email-templates/form";
        }

        service.update(id, emailTemplateForm);
        redirect.addFlashAttribute("success", "Modelo atualizado com sucesso.");
        return "redirect:/templates";
    }

    @PostMapping("/{id}/active")
    public String changeActive(
            @PathVariable UUID id,
            @RequestParam boolean active,
            RedirectAttributes redirect
    ) {
        service.changeActive(id, active);
        redirect.addFlashAttribute("success", active ? "Modelo restaurado." : "Modelo arquivado.");
        return "redirect:/templates";
    }
}
