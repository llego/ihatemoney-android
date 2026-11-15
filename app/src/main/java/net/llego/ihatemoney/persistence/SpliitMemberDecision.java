package net.llego.ihatemoney.persistence;

import java.util.Map;
import java.util.Objects;

import net.llego.ihatemoney.model.DBBill;

/** Conservative decisions for one participant against a freshly fetched group. */
final class SpliitMemberDecision {
    enum Action { CREATE, UPDATE, ACKNOWLEDGE, CONFLICT }

    private SpliitMemberDecision() { }

    /** Preserve offline creates, and never alter an uncertain or conflicted write. */
    static int renamedState(int state, String remoteId, String baseline, String attempt, String conflict) {
        if (attempt != null || conflict != null) return -1;
        if (state == DBBill.STATE_ADDED && remoteId == null) return DBBill.STATE_ADDED;
        if (remoteId != null && baseline != null
                && (state == DBBill.STATE_OK || state == DBBill.STATE_EDITED)) return DBBill.STATE_EDITED;
        return -1;
    }

    static Action rename(String baseline, String local, String remote) {
        if (remote == null || baseline == null) return Action.CONFLICT;
        if (Objects.equals(local, remote)) return Action.ACKNOWLEDGE;
        return Objects.equals(baseline, remote) ? Action.UPDATE : Action.CONFLICT;
    }

    static Action create(String name, Map<String, String> current, boolean attempted) {
        // The server creates the ID. Even a matching new participant could have been
        // created concurrently by someone else, so its identity cannot be inferred.
        if (attempted) return Action.CONFLICT;
        return current.containsValue(name) ? Action.CONFLICT : Action.CREATE;
    }
}
