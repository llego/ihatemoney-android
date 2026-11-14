package net.llego.ihatemoney.persistence;

import com.google.gson.JsonObject;

import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.model.DBMember;
import net.llego.ihatemoney.model.DBProject;
import net.llego.ihatemoney.model.SyncProgress.Stage;
import net.llego.ihatemoney.util.SpliitClient;
import net.llego.ihatemoney.util.SpliitExpenseCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import at.bitfire.cert4android.CustomCertManager;

/** Expense-only writes. Dirty rows are never reconciled by the pull path. */
final class SpliitExpenseSync {
    private final MoneyBusterSQLiteOpenHelper db;
    private final SpliitSyncStore store;
    private final SpliitClient client;
    private final CustomCertManager certificates;
    private final SpliitClient.ProgressListener progress;

    SpliitExpenseSync(MoneyBusterSQLiteOpenHelper db, SpliitClient client,
                      CustomCertManager certificates, SpliitClient.ProgressListener progress) {
        this.db = db;
        this.store = new SpliitSyncStore(db);
        this.client = client;
        this.certificates = certificates;
        this.progress = progress;
    }

    boolean push(DBProject project, SpliitClient.SpliitGroup group) throws Exception {
        Map<String, SpliitClient.SpliitExpense> remote = new HashMap<>();
        for (SpliitClient.SpliitExpense expense : group.expenses) remote.put(expense.id, expense);
        Map<Long, DBMember> members = new HashMap<>();
        for (DBMember member : db.getMembersOfProject(project.getId(), null)) {
            // A participant removed remotely must never be uploaded in a pending edit.
            for (SpliitClient.SpliitParticipant participant : group.participants) {
                if (participant.id.equals(member.getRemoteIdStr())) members.put(member.getId(), member);
            }
        }
        boolean changed = false;
        List<DBBill> pending = db.getBillsOfProject(project.getId());
        pending.removeIf(bill -> bill.getState() == DBBill.STATE_OK
                && store.get(bill.getId(), SpliitSyncStore.ATTEMPT) == null);
        int processed = 0;
        progress.onProgress(Stage.UPLOADING, 0, pending.size());
        for (DBBill snapshot : pending) {
            progress.onProgress(Stage.UPLOADING, processed++, pending.size());
            DBBill bill = db.getBill(snapshot.getId());
            if (bill == null || store.get(bill.getId(), SpliitSyncStore.CONFLICT) != null) continue;
            String baseline = store.get(bill.getId(), SpliitSyncStore.BASELINE);
            String attempt = store.get(bill.getId(), SpliitSyncStore.ATTEMPT);
            SpliitClient.SpliitExpense current = remote.get(bill.getRemoteIdStr());
            JsonObject sentAttempt = null;
            if (attempt != null) {
                sentAttempt = SpliitExpenseCodec.parse(attempt);
                if (sentAttempt.has("id")) {
                    current = remote.get(sentAttempt.get("id").getAsString());
                }
            }
            SpliitSyncDecision.Action action = SpliitSyncDecision.decide(bill.getState(),
                    bill.getRemoteIdStr(), baseline, current == null ? null : current.raw, attempt);
            if (action == SpliitSyncDecision.Action.SKIP) continue;
            if (action == SpliitSyncDecision.Action.ACKNOWLEDGE) {
                acknowledge(bill, current, sentAttempt.get("form").toString(), members,
                        sentAttempt.get("state").getAsInt());
                changed = true;
                continue;
            }
            if (action == SpliitSyncDecision.Action.REMOVE_LOCAL) {
                db.deleteBill(bill.getId());
                continue;
            }
            if (action == SpliitSyncDecision.Action.CONFLICT) {
                store.put(bill.getId(), SpliitSyncStore.CONFLICT, current == null ? "null" : current.raw);
                continue;
            }
            boolean create = action == SpliitSyncDecision.Action.CREATE;
            if (!create) {
                // Spliit has no compare-and-swap API. Recheck immediately before writing
                // to narrow (but not eliminate) the read/write race with web clients.
                SpliitClient.SpliitExpense latest = client.getExpense(bill.getRemoteIdStr(), certificates);
                if (!SpliitExpenseCodec.same(baseline, latest.raw)) {
                    store.put(bill.getId(), SpliitSyncStore.CONFLICT, latest.raw);
                    continue;
                }
            }
            if (action == SpliitSyncDecision.Action.DELETE) {
                if (!prepare(bill, "{\"delete\":true}", null, members)) continue;
                client.deleteExpense(bill.getRemoteIdStr(), certificates);
                db.deleteBill(bill.getId());
                changed = true;
            } else {
                String form = SpliitExpenseCodec.localForm(bill, baseline, members).toString();
                String id = create ? UUID.randomUUID().toString().replace("-", "").substring(0, 21)
                        : bill.getRemoteIdStr();
                JsonObject sent = new JsonObject();
                sent.addProperty("id", id);
                sent.addProperty("create", create);
                sent.addProperty("state", bill.getState());
                sent.add("form", SpliitExpenseCodec.parse(form));
                if (!prepare(bill, sent.toString(), form, members)) continue;
                String savedId = client.saveExpense(id, create, form, certificates);
                // Persist a server-minted ID before doing any more network I/O.
                sent.addProperty("id", savedId);
                store.put(bill.getId(), SpliitSyncStore.ATTEMPT, sent.toString());
                SpliitClient.SpliitExpense saved = client.getExpense(savedId, certificates);
                acknowledge(bill, saved, form, members, bill.getState());
                changed = true;
            }
        }
        progress.onProgress(Stage.UPLOADING, pending.size(), pending.size());
        return changed;
    }

    /** Claim the exact local version atomically. Editors refuse writes while a claim exists. */
    private boolean prepare(DBBill expected, String attempt, String form, Map<Long, DBMember> members) {
        android.database.sqlite.SQLiteDatabase database = db.getWritableDatabase();
        database.beginTransaction();
        try {
            DBBill latest = db.getBill(expected.getId());
            if (latest == null || latest.getState() != expected.getState()
                    || store.get(latest.getId(), SpliitSyncStore.CONFLICT) != null) return false;
            if (form != null && !SpliitExpenseCodec.same(form, SpliitExpenseCodec.localForm(latest,
                    store.get(latest.getId(), SpliitSyncStore.BASELINE), members).toString())) return false;
            store.put(latest.getId(), SpliitSyncStore.ATTEMPT, attempt);
            database.setTransactionSuccessful();
            return true;
        } finally {
            database.endTransaction();
        }
    }

    private void acknowledge(DBBill sentBill, SpliitClient.SpliitExpense saved, String form,
                             Map<Long, DBMember> members, int sentState) {
        android.database.sqlite.SQLiteDatabase database = db.getWritableDatabase();
        database.beginTransaction();
        try {
            DBBill latest = db.getBill(sentBill.getId());
            if (latest == null) return;
            boolean unchanged = latest.getState() == sentState
                    && SpliitExpenseCodec.same(form, SpliitExpenseCodec.localForm(latest,
                    store.get(latest.getId(), SpliitSyncStore.BASELINE), members).toString());
            db.updateBill(latest.getId(), null, saved.id, null, null, null, null,
                    unchanged ? DBBill.STATE_OK : latest.getState(), null, null, null, null, null);
            store.put(latest.getId(), SpliitSyncStore.BASELINE, saved.raw);
            store.put(latest.getId(), SpliitSyncStore.ATTEMPT, null);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }
}
