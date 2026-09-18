package br.com.mailflow.inbox;

import br.com.mailflow.security.CurrentWorkspace;
import br.com.mailflow.settings.smtp.SmtpAccountService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.UUID;

@Controller
public class InboxController {
    private final InboxService inbox;
    private final SmtpAccountService accounts;
    private final CurrentWorkspace workspace;
    public InboxController(InboxService inbox,SmtpAccountService accounts,CurrentWorkspace workspace) { this.inbox=inbox; this.accounts=accounts; this.workspace=workspace; }
    @GetMapping("/inbox") String list(@RequestParam(defaultValue="0") int page,Model model) {
        var replies=inbox.list(page); model.addAttribute("replies",replies.stream().limit(50).toList()); model.addAttribute("hasNext",replies.size()>50);
        model.addAttribute("page",page); model.addAttribute("connections",inbox.connections()); return "inbox/list";
    }
    @PostMapping("/inbox/accounts/{id}") String configure(@PathVariable UUID id,@RequestParam boolean enabled,RedirectAttributes flash) {
        try { inbox.configure(id,enabled); flash.addFlashAttribute("success",enabled?"Leitura autorizada. As respostas serão consultadas enquanto o aplicativo estiver aberto.":"Leitura desligada. As respostas já salvas permanecem aqui."); }
        catch(IllegalArgumentException ex) { flash.addFlashAttribute("error",ex.getMessage()); }
        return "redirect:/inbox";
    }
    @PostMapping("/inbox/accounts/{id}/sync") String sync(@PathVariable UUID id,RedirectAttributes flash) {
        accounts.get(id); var result=inbox.sync(id,workspace.id());
        String message=switch(result.status()) {
            case "UPDATED" -> "Respostas atualizadas: "+result.imported()+" nova(s).";
            case "BUSY" -> "Aguarde a atualização em andamento ou confira o limite de respostas locais.";
            case "DISABLED" -> "Autorize a leitura da sua conta para atualizar as respostas.";
            default -> "Não foi possível atualizar. Confira o acesso ao Gmail e tente novamente.";
        };
        flash.addFlashAttribute("FAILED".equals(result.status())?"error":"success",message); return "redirect:/inbox";
    }
    @PostMapping("/inbox/{id}/delete") String remove(@PathVariable UUID id,RedirectAttributes flash) {
        inbox.remove(id); flash.addFlashAttribute("success","Cópia local removida. Seu e-mail no Gmail foi preservado."); return "redirect:/inbox";
    }
}
