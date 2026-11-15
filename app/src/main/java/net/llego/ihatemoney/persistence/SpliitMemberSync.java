package net.llego.ihatemoney.persistence;

import android.database.sqlite.SQLiteDatabase;

import com.google.gson.JsonObject;

import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.model.DBMember;
import net.llego.ihatemoney.model.DBProject;
import net.llego.ihatemoney.util.SpliitClient;
import net.llego.ihatemoney.util.SpliitGroupForm;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import at.bitfire.cert4android.CustomCertManager;

/** Participant writes are made one at a time from a fresh, complete group snapshot. */
final class SpliitMemberSync {
    private final MoneyBusterSQLiteOpenHelper db;
    private final SpliitMemberStore store;
    private final SpliitClient client;
    private final CustomCertManager certificates;

    SpliitMemberSync(MoneyBusterSQLiteOpenHelper db, SpliitClient client, CustomCertManager certificates) {
        this.db = db;
        this.store = new SpliitMemberStore(db);
        this.client = client;
        this.certificates = certificates;
    }

    boolean push(DBProject project) throws Exception {
        boolean changed = false;
        for (DBMember snapshot : db.getMembersOfProject(project.getId(), null)) {
            if (snapshot.getState() != DBBill.STATE_ADDED && snapshot.getState() != DBBill.STATE_EDITED) continue;
            if (store.get(snapshot.getId(), SpliitMemberStore.CONFLICT) != null) continue;
            SpliitClient.SpliitGroup group = client.getGroupMetadata(certificates);
            Map<String, String> remote = participants(group);
            String attempt = store.get(snapshot.getId(), SpliitMemberStore.ATTEMPT);
            boolean create = snapshot.getRemoteIdStr() == null;
            SpliitMemberDecision.Action action = create
                    ? SpliitMemberDecision.create(snapshot.getName(), remote, attempt != null)
                    : SpliitMemberDecision.rename(store.get(snapshot.getId(), SpliitMemberStore.BASELINE),
                    snapshot.getName(), remote.get(snapshot.getRemoteIdStr()));
            if (action == SpliitMemberDecision.Action.CONFLICT) {
                store.put(snapshot.getId(), SpliitMemberStore.CONFLICT,
                        create ? "null" : encodeRemote(remote.get(snapshot.getRemoteIdStr())));
                continue;
            }
            if (action == SpliitMemberDecision.Action.ACKNOWLEDGE) {
                acknowledge(snapshot, snapshot.getRemoteIdStr());
                continue;
            }
            JsonObject form = create ? SpliitGroupForm.add(group.raw, snapshot.getName())
                    : SpliitGroupForm.rename(group.raw, snapshot.getRemoteIdStr(), snapshot.getName());
            // There is no server-side compare-and-swap. This fresh comparison narrows,
            // but cannot eliminate, the race with another client updating the group.
            SpliitClient.SpliitGroup latest = client.getGroupMetadata(certificates);
            if (!SpliitGroupForm.fromSnapshot(group.raw).equals(SpliitGroupForm.fromSnapshot(latest.raw))) continue;
            if (!claim(snapshot, form.toString())) continue;
            client.updateGroup(form.toString(), certificates);
            SpliitClient.SpliitGroup saved = client.getGroupMetadata(certificates);
            Map<String, String> after = participants(saved);
            String id = snapshot.getRemoteIdStr();
            if (create) {
                for (Map.Entry<String, String> entry : after.entrySet()) {
                    if (!remote.containsKey(entry.getKey()) && snapshot.getName().equals(entry.getValue())) {
                        if (id != null) { id = null; break; }
                        id = entry.getKey();
                    }
                }
            }
            if (id == null || !snapshot.getName().equals(after.get(id))) {
                store.put(snapshot.getId(), SpliitMemberStore.CONFLICT, "null");
            } else {
                acknowledge(snapshot, id);
                changed = true;
            }
        }
        return changed;
    }

    private boolean claim(DBMember expected, String form) {
        SQLiteDatabase database = db.getWritableDatabase();
        database.beginTransaction();
        try {
            DBMember current = db.getMember(expected.getId());
            if (current == null || current.getState() != expected.getState()
                    || !Objects.equals(current.getName(), expected.getName())
                    || !Objects.equals(current.getRemoteIdStr(), expected.getRemoteIdStr())
                    || store.get(expected.getId(), SpliitMemberStore.CONFLICT) != null) return false;
            store.put(expected.getId(), SpliitMemberStore.ATTEMPT, form);
            database.setTransactionSuccessful();
            return true;
        } finally {
            database.endTransaction();
        }
    }

    private void acknowledge(DBMember sent, String remoteId) {
        SQLiteDatabase database = db.getWritableDatabase();
        database.beginTransaction();
        try {
            DBMember current = db.getMember(sent.getId());
            if (current == null) return;
            boolean unchanged = current.getState() == sent.getState() && Objects.equals(current.getName(), sent.getName());
            db.updateMember(current.getId(), null, null, null,
                    unchanged ? DBBill.STATE_OK : DBBill.STATE_EDITED, null, remoteId,
                    null, null, null, null, null);
            store.put(current.getId(), SpliitMemberStore.BASELINE, sent.getName());
            store.put(current.getId(), SpliitMemberStore.ATTEMPT, null);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private static Map<String, String> participants(SpliitClient.SpliitGroup group) {
        Map<String, String> result = new HashMap<>();
        for (SpliitClient.SpliitParticipant participant : group.participants) result.put(participant.id, participant.name);
        return result;
    }

    private static String encodeRemote(String name) {
        JsonObject value = new JsonObject();
        if (name == null) value.add("name", com.google.gson.JsonNull.INSTANCE);
        else value.addProperty("name", name);
        return value.toString();
    }
}
