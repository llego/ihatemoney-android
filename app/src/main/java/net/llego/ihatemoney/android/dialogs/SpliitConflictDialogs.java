package net.llego.ihatemoney.android.dialogs;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.model.DBBillOwer;
import net.llego.ihatemoney.model.DBMember;
import net.llego.ihatemoney.model.DBProject;
import net.llego.ihatemoney.persistence.MoneyBusterSQLiteOpenHelper;
import net.llego.ihatemoney.persistence.SpliitSyncStore;
import net.llego.ihatemoney.theme.ThemedMaterialAlertDialogBuilder;
import net.llego.ihatemoney.util.MoneyFormatter;
import net.llego.ihatemoney.util.SpliitExpenseCodec;

import java.text.DateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Shows one durable conflict at a time, including conflicts on hidden deletion tombstones. */
public final class SpliitConflictDialogs {
    private final Activity activity;
    private final MoneyBusterSQLiteOpenHelper db;
    private final SpliitSyncStore store;
    private final Runnable onResolved;
    private boolean showing;

    public SpliitConflictDialogs(Activity activity, MoneyBusterSQLiteOpenHelper db, Runnable onResolved) {
        this.activity = activity;
        this.db = db;
        this.store = new SpliitSyncStore(db);
        this.onResolved = onResolved;
    }

    public void show(long projectId) {
        if (showing || activity.isFinishing() || activity.isDestroyed()) return;
        DBProject project = db.getProject(projectId);
        if (project == null || !project.isReadOnly()) return;
        for (DBBill expense : db.getBillsOfProject(projectId)) {
            String remote = store.get(expense.getId(), SpliitSyncStore.CONFLICT);
            if (remote == null) continue;
            boolean uncertainCreate = false;
            String attempt = store.get(expense.getId(), SpliitSyncStore.ATTEMPT);
            if (attempt != null) {
                JsonObject sent = SpliitExpenseCodec.parse(attempt);
                uncertainCreate = sent.has("create") && sent.get("create").getAsBoolean();
            }
            boolean supported = "null".equals(remote) || SpliitExpenseCodec.editable(remote);
            String remoteDescription = "null".equals(remote)
                    ? activity.getString(R.string.spliit_remote_deleted) : describe(remote, project);
            String message = activity.getString(R.string.spliit_conflict_message,
                    describe(expense, project), remoteDescription);
            if (uncertainCreate) message += "\n\n" + activity.getString(R.string.spliit_uncertain_create);
            if (!supported) message += "\n\n" + activity.getString(R.string.spliit_unsupported_conflict);
            showing = true;
            AlertDialog.Builder builder = new ThemedMaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.spliit_conflict_title).setMessage(message)
                    .setNegativeButton(R.string.spliit_use_server, (dialog, which) -> resolve(expense, false))
                    .setNeutralButton(R.string.spliit_later, (dialog, which) -> { });
            if (supported) builder.setPositiveButton(R.string.spliit_keep_local,
                    (dialog, which) -> resolve(expense, true));
            AlertDialog dialog = builder.create();
            dialog.setOnDismissListener(ignored -> showing = false);
            dialog.show();
            return;
        }
    }

    private void resolve(DBBill expense, boolean keepLocal) {
        store.resolve(expense.getId(), keepLocal);
        onResolved.run();
    }

    private String describe(DBBill expense, DBProject project) {
        List<String> participants = new ArrayList<>();
        for (DBBillOwer ower : expense.getBillOwers()) participants.add(name(db.getMember(ower.getMemberId())));
        String description = describe(expense.getWhat(), expense.getAmount(), expense.getTimestamp(),
                name(db.getMember(expense.getPayerId())), participants, expense.getComment(), project);
        return expense.getState() == DBBill.STATE_DELETED
                ? description + "\n" + activity.getString(R.string.spliit_pending_deletion) : description;
    }

    private String describe(String rawJson, DBProject project) {
        JsonObject raw = SpliitExpenseCodec.parse(rawJson);
        List<String> participants = new ArrayList<>();
        for (JsonElement element : raw.getAsJsonArray("paidFor")) {
            String id = element.getAsJsonObject().get("participantId").getAsString();
            DBMember member = db.getMemberByRemoteIdStr(id, project.getId());
            participants.add(member == null ? id : member.getName());
        }
        return describe(raw.get("title").getAsString(), raw.get("amount").getAsDouble() / 100.0,
                Instant.parse(raw.get("expenseDate").getAsString()).getEpochSecond(),
                raw.getAsJsonObject("paidBy").get("name").getAsString(), participants,
                SpliitExpenseCodec.text(raw, "notes", ""), project);
    }

    private String describe(String title, double amount, long timestamp, String payer,
                            List<String> participants, String notes, DBProject project) {
        return activity.getString(R.string.spliit_conflict_version, title,
                MoneyFormatter.createDisplayFormat(Locale.getDefault()).format(amount) + " " + project.getCurrencyName(),
                DateFormat.getDateTimeInstance().format(new Date(timestamp * 1000)),
                payer, String.join(", ", participants), notes == null ? "" : notes);
    }

    private String name(DBMember member) {
        return member == null ? activity.getString(R.string.spliit_unknown_participant) : member.getName();
    }
}
