package br.com.mailflow.delivery;

import br.com.mailflow.contact.ContactService;
import br.com.mailflow.contact.ContactStatus;
import br.com.mailflow.settings.smtp.SmtpAccountService;
import br.com.mailflow.template.EmailTemplateService;
import br.com.mailflow.security.CurrentWorkspace;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.UUID;

@Controller
public class DispatchController {
    private final DispatchService dispatch;
    private final ContactService contacts;
    private final SmtpAccountService accounts;
    private final EmailTemplateService templates;
    private final CurrentWorkspace workspace;
    private final br.com.mailflow.draft.SavedDraftService drafts;
    public DispatchController(DispatchService dispatch,ContactService contacts,SmtpAccountService accounts,EmailTemplateService templates,CurrentWorkspace workspace,br.com.mailflow.draft.SavedDraftService drafts) {
        this.dispatch=dispatch;this.contacts=contacts;this.accounts=accounts;this.templates=templates;this.workspace=workspace;this.drafts=drafts;
    }
    @InitBinder("dispatchForm")
    void bind(WebDataBinder binder) {
        binder.setAllowedFields("accountId","contactIds","subject","bodyText","bodyHtml","mode","scheduledAt","timezone","occurrences");
        binder.setAutoGrowCollectionLimit(20);
    }
    @GetMapping("/messages/new")
    String form(@RequestParam(required=false) UUID templateId,@RequestParam(required=false) String q,Model model) {
        var form=new DispatchForm();
        if(templateId!=null) {
            var template=templates.get(templateId);
            if(template.isActive()) { form.setSubject(template.getSubject());form.setBodyText(template.getBodyText());form.setBodyHtml(template.getBodyHtml()); }
        }
        model.addAttribute("dispatchForm",form); populate(model,q); return "delivery/form";
    }
    @PostMapping("/messages")
    String create(@ModelAttribute("dispatchForm") DispatchForm form,BindingResult errors,Model model,@RequestParam(defaultValue="review") String action,RedirectAttributes flash) {
        if(!errors.hasErrors()) try {
            if("save".equals(action)) { drafts.create(form); flash.addFlashAttribute("success","Rascunho salvo. Ele fica aqui até você excluí-lo."); return "redirect:/drafts"; }
            if(!"review".equals(action)) throw new IllegalArgumentException("Escolha salvar ou revisar.");
            return "redirect:/messages/"+dispatch.createDraft(form);
        }
        catch(IllegalArgumentException ex) {errors.reject("message",ex.getMessage());}
        populate(model,null);return "delivery/form";
    }
    private void populate(Model model,String query) {
        model.addAttribute("contacts",contacts.list(query).stream().filter(c->c.getStatus()==ContactStatus.ACTIVE).toList());
        model.addAttribute("accounts",accounts.list().stream().filter(a->a.isEnabled()).toList());
        model.addAttribute("templates",templates.list().stream().filter(t->t.isActive()).toList());
    }
    @GetMapping("/messages/{id}")
    String review(@PathVariable UUID id,Model model) {
        var detail=dispatch.get(id);
        model.addAttribute("detail",detail);model.addAttribute("message",detail.message());
        model.addAttribute("ownEmail",workspace.principal().getUsername());return "delivery/review";
    }
    @GetMapping({"/history","/schedules","/automations"})
    String list(jakarta.servlet.http.HttpServletRequest request,Model model) {
        var kind=request.getRequestURI().substring(1);
        String title=switch(kind){case "schedules"->"Agendamentos";case "automations"->"Automações";default->"Histórico e rascunhos";};
        model.addAttribute("title",title);model.addAttribute("kind",kind);model.addAttribute("messages",dispatch.list(kind));return "delivery/list";
    }
    @PostMapping("/messages/{id}/confirm")
    String confirm(@PathVariable UUID id,RedirectAttributes flash) {
        try {dispatch.confirm(id);flash.addFlashAttribute("success","Envio confirmado. Acompanhe o resultado abaixo; a fila funciona enquanto o aplicativo estiver aberto.");}
        catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/messages/"+id;
    }
    @PostMapping("/messages/{id}/test")
    String test(@PathVariable UUID id,RedirectAttributes flash) {
        try {var testId=dispatch.selfTest(id);flash.addFlashAttribute("success","Teste solicitado somente para o e-mail da sua conta. Os destinatários originais não foram acionados.");return "redirect:/messages/"+testId;}
        catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());return "redirect:/messages/"+id;}
    }
    @PostMapping("/messages/{id}/cancel")
    String cancel(@PathVariable UUID id,RedirectAttributes flash) {
        dispatch.cancel(id);flash.addFlashAttribute("success","Envios pendentes cancelados. Uma mensagem que já estava sendo transmitida pode terminar.");return "redirect:/messages/"+id;
    }
    @PostMapping("/messages/{id}/pause")
    String pause(@PathVariable UUID id,RedirectAttributes flash) {return pauseState(id,true,flash);}
    @PostMapping("/messages/{id}/resume")
    String resume(@PathVariable UUID id,RedirectAttributes flash) {return pauseState(id,false,flash);}
    private String pauseState(UUID id,boolean paused,RedirectAttributes flash) {
        try {dispatch.pause(id,paused);flash.addFlashAttribute("success",paused?"Fila pausada. Uma transmissão já iniciada pode terminar.":"Fila retomada. Horários perdidos há mais de 15 minutos precisarão de revisão.");}
        catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/messages/"+id;
    }
    @PostMapping("/messages/{id}/jobs/{jobId}/release")
    String release(@PathVariable UUID id,@PathVariable UUID jobId,RedirectAttributes flash) {
        try {dispatch.release(id,jobId);flash.addFlashAttribute("success","Esta mensagem foi recolocada na fila para envio agora.");}
        catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/messages/"+id;
    }
}
