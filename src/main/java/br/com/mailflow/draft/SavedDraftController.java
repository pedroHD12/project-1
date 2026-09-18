package br.com.mailflow.draft;

import br.com.mailflow.contact.*;
import br.com.mailflow.delivery.*;
import br.com.mailflow.settings.smtp.SmtpAccountService;
import br.com.mailflow.template.EmailTemplateService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.UUID;

@Controller
public class SavedDraftController {
    private final SavedDraftService drafts;
    private final DispatchService dispatch;
    private final ContactService contacts;
    private final SmtpAccountService accounts;
    private final EmailTemplateService templates;
    public SavedDraftController(SavedDraftService drafts,DispatchService dispatch,ContactService contacts,SmtpAccountService accounts,EmailTemplateService templates) {
        this.drafts=drafts; this.dispatch=dispatch; this.contacts=contacts; this.accounts=accounts; this.templates=templates;
    }
    @InitBinder("dispatchForm") void bind(WebDataBinder binder) {
        binder.setAllowedFields("accountId","contactIds","subject","bodyText","bodyHtml","mode","scheduledAt","timezone","occurrences");
        binder.setAutoGrowCollectionLimit(20);
    }
    @GetMapping("/drafts") String list(@RequestParam(defaultValue="0") int page,Model model) {
        var items=drafts.list(page);
        model.addAttribute("drafts",items.stream().limit(50).toList()); model.addAttribute("page",page); model.addAttribute("hasNext",items.size()>50);
        return "drafts/list";
    }
    @GetMapping("/drafts/{id}/edit") String edit(@PathVariable UUID id,Model model) {
        var draft=drafts.get(id); model.addAttribute("dispatchForm",draft.form()); populate(id,draft.version(),draft.form(),model); return "delivery/form";
    }
    @PostMapping("/drafts/{id}") String update(@PathVariable UUID id,@RequestParam long version,@RequestParam(defaultValue="review") String action,
        @ModelAttribute("dispatchForm") DispatchForm form,BindingResult errors,Model model,RedirectAttributes flash) {
        if(!errors.hasErrors()) try {
            if(!"save".equals(action) && !"review".equals(action)) throw new IllegalArgumentException("Escolha salvar ou revisar.");
            version=drafts.update(id,version,form);
            if("save".equals(action)) { flash.addFlashAttribute("success","Rascunho salvo. Ele fica aqui até você excluí-lo."); return "redirect:/drafts"; }
            return "redirect:/messages/"+dispatch.createDraft(form);
        } catch(IllegalArgumentException ex) { errors.reject("draft",ex.getMessage()); }
        populate(id,version,form,model); return "delivery/form";
    }
    private void populate(UUID id,long version,DispatchForm form,Model model) {
        model.addAttribute("savedDraftId",id); model.addAttribute("draftVersion",version);
        model.addAttribute("contacts",contacts.listForDraft(form.getContactIds()));
        model.addAttribute("accounts",accounts.list().stream().filter(a->a.isEnabled()).toList());
        model.addAttribute("templates",templates.list().stream().filter(t->t.isActive()).toList());
    }
    @PostMapping("/drafts/{id}/delete") String delete(@PathVariable UUID id,RedirectAttributes flash) {
        drafts.delete(id); flash.addFlashAttribute("success","Rascunho excluído por você."); return "redirect:/drafts";
    }
}
