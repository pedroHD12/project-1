package br.com.mailflow.dashboard;

public record DashboardSnapshot(
        long contacts,
        long templates,
        long activeSchedules,
        long pendingDeliveries
) {
}

