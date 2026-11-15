package net.llego.ihatemoney.persistence;

import java.util.Map;
import java.util.Objects;

/** Conservative decisions for one participant against a freshly fetched group. */
final class SpliitMemberDecision {
    enum Action { CREATE, UPDATE, ACKNOWLEDGE, CONFLICT }

    private SpliitMemberDecision() { }

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
