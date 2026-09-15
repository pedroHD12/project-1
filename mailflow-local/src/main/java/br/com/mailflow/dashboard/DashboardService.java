package br.com.mailflow.dashboard;

import br.com.mailflow.contact.ContactRepository;
import br.com.mailflow.template.EmailTemplateRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final ContactRepository contactRepository;
    private final EmailTemplateRepository templateRepository;
    private final JdbcClient jdbcClient;
    private final br.com.mailflow.security.CurrentWorkspace workspace;

    public DashboardService(
            ContactRepository contactRepository,
            EmailTemplateRepository templateRepository,
            JdbcClient jdbcClient,
            br.com.mailflow.security.CurrentWorkspace workspace
    ) {
        this.contactRepository = contactRepository;
        this.templateRepository = templateRepository;
        this.jdbcClient = jdbcClient;
        this.workspace = workspace;
    }

    @Transactional(readOnly = true)
    public DashboardSnapshot snapshot() {
        var activeSchedules = count("select count(*) from schedules where enabled = true and workspace_id = :workspaceId");
        var pendingDeliveries = count("select count(*) from delivery_jobs where status = 'PENDING' and workspace_id = :workspaceId");

        return new DashboardSnapshot(
                contactRepository.countByWorkspaceId(workspace.id()),
                templateRepository.countByWorkspaceId(workspace.id()),
                activeSchedules,
                pendingDeliveries
        );
    }

    private long count(String sql) {
        return jdbcClient.sql(sql).param("workspaceId", workspace.id()).query(Long.class).single();
    }
}
