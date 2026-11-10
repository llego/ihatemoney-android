package net.llego.ihatemoney.persistence;

/** Shared SQL for loading expenses and their participants without per-expense queries. */
final class BillQueries {
    private BillQueries() {}

    static final String[] SYNC_INDEXES = {
            "CREATE INDEX IF NOT EXISTS BILLS_PROJECTID_STATE_idx ON BILLS(PROJECTID, STATE)",
            "CREATE INDEX IF NOT EXISTS BILLOWERS_BILLID_idx ON BILLOWERS(BILLID)",
            "CREATE INDEX IF NOT EXISTS BILLOWERS_MEMBERID_idx ON BILLOWERS(MEMBERID)",
            "CREATE INDEX IF NOT EXISTS MEMBERS_PROJECTID_REMOTEID_idx ON MEMBERS(PROJECTID, REMOTEID)"
    };

    static String participantsForSelection(String billSelection) {
        // Reuse bound arguments, not a long IN (?, ...) list that can exceed SQLite's limit.
        return "BILLID IN (SELECT ID FROM BILLS"
                + (billSelection.isEmpty() ? "" : " WHERE " + billSelection) + ")";
    }
}
