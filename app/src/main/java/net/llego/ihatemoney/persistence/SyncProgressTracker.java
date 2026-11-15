package net.llego.ihatemoney.persistence;

import net.llego.ihatemoney.model.SyncProgress;

import java.util.HashMap;
import java.util.Map;

/** Keeps the latest snapshot across screen recreation, without broadcasting every database row. */
public final class SyncProgressTracker {
    private static final long UPDATE_INTERVAL_MS = 250;
    private final Map<Long, SyncProgress> current = new HashMap<>();
    private final Map<Long, SyncProgress> published = new HashMap<>();
    private final Map<Long, Long> publishedAt = new HashMap<>();
    private final Map<Long, Long> operations = new HashMap<>();

    public synchronized SyncProgress get(long projectId) { return current.get(projectId); }

    /** Always retain progress; return true only when the UI needs a new event. */
    public synchronized boolean update(SyncProgress progress, long now) {
        return update(0, progress, now);
    }

    public synchronized boolean update(long operationId, SyncProgress progress, long now) {
        current.put(progress.projectId, progress);
        operations.put(progress.projectId, operationId);
        SyncProgress previous = published.get(progress.projectId);
        if (previous != null && previous.stage == progress.stage && previous.total == progress.total
                && previous.completed == progress.completed) return false;
        boolean publish = previous == null || previous.stage != progress.stage || previous.total != progress.total
                || (progress.hasTotal() && progress.completed == progress.total)
                || now - publishedAt.get(progress.projectId) >= UPDATE_INTERVAL_MS;
        if (publish) {
            published.put(progress.projectId, progress);
            publishedAt.put(progress.projectId, now);
        }
        return publish;
    }

    public synchronized void clear(long projectId) {
        current.remove(projectId);
        published.remove(projectId);
        publishedAt.remove(projectId);
        operations.remove(projectId);
    }

    public synchronized boolean clear(long projectId, long operationId) {
        if (!Long.valueOf(operationId).equals(operations.get(projectId))) return false;
        clear(projectId);
        return true;
    }

    public synchronized boolean queueIfIdle(SyncProgress progress, long now) {
        return !current.containsKey(progress.projectId) && update(progress, now);
    }
}
