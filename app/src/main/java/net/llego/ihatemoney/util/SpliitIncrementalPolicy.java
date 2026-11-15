package net.llego.ihatemoney.util;

import java.util.Set;

/** Conservative selection of expense details after a complete activity scan. */
public final class SpliitIncrementalPolicy {
    public static final long AUDIT_INTERVAL_MS = 24L * 60 * 60 * 1000;

    private SpliitIncrementalPolicy() { }

    public static boolean fullAudit(long now, long lastAudit, boolean activityScanComplete) {
        return !activityScanComplete || lastAudit <= 0 || now < lastAudit
                || now - lastAudit >= AUDIT_INTERVAL_MS;
    }

    public static boolean download(String id, boolean fullAudit, boolean hasValidBaseline,
                                   boolean pendingLocalChanges, Set<String> changedIds) {
        return fullAudit || !hasValidBaseline || pendingLocalChanges || changedIds.contains(id);
    }
}
