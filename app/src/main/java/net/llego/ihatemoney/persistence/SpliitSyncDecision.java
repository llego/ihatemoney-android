package net.llego.ihatemoney.persistence;

import com.google.gson.JsonObject;

import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.util.SpliitExpenseCodec;

/** Pure reconciliation policy, shared by real sync and offline/retry regression tests. */
final class SpliitSyncDecision {
    enum Action { SKIP, CREATE, UPDATE, DELETE, REMOVE_LOCAL, ACKNOWLEDGE, CONFLICT }

    static Action decide(int state, String remoteId, String baseline, String current, String attempt) {
        if (state == DBBill.STATE_OK && attempt == null) return Action.SKIP;
        if (attempt != null) {
            JsonObject sent = SpliitExpenseCodec.parse(attempt);
            if (sent.has("form")) {
                if (current != null && SpliitExpenseCodec.same(sent.get("form").toString(),
                        SpliitExpenseCodec.remoteForm(current).toString())) return Action.ACKNOWLEDGE;
                // Older Spliit versions ignore client IDs. Never blindly repeat a create.
                if (sent.get("create").getAsBoolean()) return Action.CONFLICT;
            } else if (state != DBBill.STATE_DELETED) {
                return Action.CONFLICT;
            }
        }
        if (state == DBBill.STATE_DELETED && current == null) return Action.REMOVE_LOCAL;
        if (baseline == null && remoteId == null) return Action.CREATE;
        if (current == null || baseline == null || !SpliitExpenseCodec.same(baseline, current)
                || !SpliitExpenseCodec.editable(baseline)) return Action.CONFLICT;
        return state == DBBill.STATE_DELETED ? Action.DELETE : Action.UPDATE;
    }
}
