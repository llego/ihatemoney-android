package net.eneiluj.moneybuster.persistence;

import net.eneiluj.moneybuster.model.ProjectType;

/** SQL ordering for the expense list and its search results. */
final class BillListOrder {
    private BillListOrder() {}

    static String forProject(ProjectType type) {
        if (ProjectType.IHATEMONEY.equals(type)) {
            // IHateMoney dates have no time. A local bill still has the entry time
            // until sync, so compare calendar dates rather than raw timestamps.
            // For synced bills, match IHateMoney's date/creation_date/id order.
            // Pending new bills come first within their day, newest local ID first.
            return "strftime('%Y-%m-%d', TIMESTAMP, 'unixepoch', 'localtime') DESC, "
                    + "(COALESCE(REMOTEID, 0) = 0) DESC, CREATIONDATE DESC, REMOTEID DESC, ID DESC";
        }
        return "TIMESTAMP DESC";
    }
}
