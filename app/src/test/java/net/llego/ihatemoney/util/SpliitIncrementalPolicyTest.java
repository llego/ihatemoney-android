package net.llego.ihatemoney.util;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

public class SpliitIncrementalPolicyTest {
    @Test
    public void missingHistoryClockRollbackAndExpiredAuditAlwaysForceFullDownload() {
        long interval = SpliitIncrementalPolicy.AUDIT_INTERVAL_MS;
        assertTrue(SpliitIncrementalPolicy.fullAudit(interval + 1, 0, true));
        assertTrue(SpliitIncrementalPolicy.fullAudit(interval + 1, 1, false));
        assertTrue(SpliitIncrementalPolicy.fullAudit(1, 2, true));
        assertTrue(SpliitIncrementalPolicy.fullAudit(interval + 1, 1, true));
        assertFalse(SpliitIncrementalPolicy.fullAudit(interval, 1, true));
    }

    @Test
    public void pendingOrChangedExpensesNeverUseCachedDetails() {
        Set<String> changed = Set.of("changed");
        assertFalse(SpliitIncrementalPolicy.download("clean", false, true, false, changed));
        assertTrue(SpliitIncrementalPolicy.download("changed", false, true, false, changed));
        assertTrue(SpliitIncrementalPolicy.download("clean", false, false, false, changed));
        assertTrue(SpliitIncrementalPolicy.download("clean", false, true, true, changed));
        assertTrue(SpliitIncrementalPolicy.download("clean", true, true, false, changed));
    }
}
