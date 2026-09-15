package br.com.mailflow.contact;

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
@RequestMapping("/contacts")
public class ContactController {

    private final ContactService service;

    public ContactController(ContactService service) {
        this.service = service;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("contacts", service.list(q));
        model.addAttribute("query", q == null ? "" : q);
        return "contacts/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("contactForm", new ContactForm());
        model.addAttribute("formTitle", "Novo contato");
        model.addAttribute("formAction", "/contacts");
        return "contacts/form";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute ContactForm contactForm,
            BindingResult binding,
            Model model,
            RedirectAttributes redirect
    ) {
        if (!binding.hasErrors()) {
            try {
                service.create(contactForm);
                redirect.addFlashAttribute("success", "Contato cadastrado com sucesso.");
                return "redirect:/contacts";
            } catch (DuplicateContactException exception) {
                binding.rejectValue("email", "duplicate", exception.getMessage());
            } catch (org.springframework.dao.DataIntegrityViolationException exception) {
                binding.rejectValue("email", "conflict", "Não foi possível salvar. Confira se este e-mail já está cadastrado.");
            }
        }

        model.addAttribute("formTitle", "Novo contato");
        model.addAttribute("formAction", "/contacts");
        return "contacts/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("contactForm", ContactForm.from(service.get(id)));
        model.addAttribute("formTitle", "Editar contato");
        model.addAttribute("formAction", "/contacts/" + id);
        return "contacts/form";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute ContactForm contactForm,
            BindingResult binding,
            Model model,
            RedirectAttributes redirect
    ) {
        if (!binding.hasErrors()) {
            try {
                service.update(id, contactForm);
                redirect.addFlashAttribute("success", "Contato atualizado com sucesso.");
                return "redirect:/contacts";
            } catch (DuplicateContactException exception) {
                binding.rejectValue("email", "duplicate", exception.getMessage());
            } catch (org.springframework.dao.DataIntegrityViolationException exception) {
                binding.rejectValue("email", "conflict", "Não foi possível salvar. Confira se este e-mail já está cadastrado.");
            }
        }

        model.addAttribute("formTitle", "Editar contato");
        model.addAttribute("formAction", "/contacts/" + id);
        return "contacts/form";
    }

    @PostMapping("/{id}/status")
    public String changeStatus(
            @PathVariable UUID id,
            @RequestParam ContactStatus status,
            RedirectAttributes redirect
    ) {
        service.changeStatus(id, status);
        redirect.addFlashAttribute("success", "Situação do contato atualizada.");
        return "redirect:/contacts";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        service.delete(id);
        redirect.addFlashAttribute("success", "Contato excluído.");
        return "redirect:/contacts";
    }
}
