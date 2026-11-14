package net.llego.ihatemoney.model;

/** Immutable, per-project progress. Counts describe the current phase, not the whole sync. */
public final class SyncProgress {
    public static final int UNKNOWN_TOTAL = -1;

    public enum Stage { QUEUED, PREPARING, PROJECT, LISTING, DOWNLOADING, UPLOADING, SAVING }

    public final long projectId;
    public final Stage stage;
    public final int completed;
    public final int total;

    public SyncProgress(long projectId, Stage stage, int completed, int total) {
        this.projectId = projectId;
        this.stage = stage;
        this.completed = Math.max(0, completed);
        this.total = total < 0 ? UNKNOWN_TOTAL : Math.max(this.completed, total);
    }

    public boolean hasTotal() { return total > 0; }

    public int percent() {
        return hasTotal() ? (int) (100L * completed / total) : 0;
    }
}
