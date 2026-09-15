package br.com.mailflow.delivery;

import br.com.mailflow.settings.smtp.SmtpAccountRepository;
import org.springframework.stereotype.Service;

@Service
public class DispatchWorker {
    private final DispatchQueue queue;
    private final SmtpAccountRepository accounts;
    private final EmailGateway gateway;
    public DispatchWorker(DispatchQueue queue,SmtpAccountRepository accounts,EmailGateway gateway) {
        this.queue=queue; this.accounts=accounts; this.gateway=gateway;
    }
    public void processOne() {
        queue.recoverInterrupted();
        var work=queue.claim();
        if(work.isEmpty()) return;
        var job=work.get();
        var outcome=EmailGateway.Outcome.UNKNOWN;
        boolean skipped=false;
        try {
            var account=accounts.findByIdAndWorkspaceId(job.accountId(),job.workspaceId());
            if(account.isEmpty() || !account.get().isEnabled() || !job.fingerprint().equals(DispatchService.fingerprint(account.get())) || !queue.permitted(job)) {
                skipped=true;
            } else {
                outcome=gateway.send(account.get(),new EmailMessage(job.from(),java.util.List.of(job.email()),job.subject(),job.text(),job.html()));
                if(outcome==null) outcome=EmailGateway.Outcome.UNKNOWN;
            }
        } catch(RuntimeException ex) {
            // Do not persist exception text, credentials, addresses or message bodies.
            outcome=EmailGateway.Outcome.UNKNOWN;
        }
        queue.finish(job,outcome,skipped);
    }
}
