package net.llego.ihatemoney.persistence;

import com.google.gson.JsonObject;

import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.util.SpliitExpenseCodec;

import org.junit.Test;

import static net.llego.ihatemoney.persistence.SpliitSyncDecision.Action.*;
import static net.llego.ihatemoney.util.SpliitFixtures.EXPENSE;
import static org.junit.Assert.assertEquals;

public class SpliitSyncDecisionTest {
    @Test
    public void createsOfflineExpenseAndUpdatesUnchangedRemoteVersion() {
        assertEquals(CREATE, decide(DBBill.STATE_ADDED, null, null, null, null));
        assertEquals(UPDATE, decide(DBBill.STATE_EDITED, "expense-1", EXPENSE, EXPENSE, null));
        assertEquals(SKIP, decide(DBBill.STATE_OK, "expense-1", EXPENSE, EXPENSE, null));
    }

    @Test
    public void bothChangedPromptsInsteadOfOverwriting() {
        assertEquals(CONFLICT, decide(DBBill.STATE_EDITED, "expense-1", EXPENSE,
                EXPENSE.replace("Lunch", "Dinner"), null));
        assertEquals(CONFLICT, decide(DBBill.STATE_EDITED, "expense-1", EXPENSE,
                EXPENSE.replace("receipt.jpg", "other.jpg"), null));
    }

    @Test
    public void deletionConflictsWithRemoteEditButAlreadyDeletedIsSuccess() {
        assertEquals(DELETE, decide(DBBill.STATE_DELETED, "expense-1", EXPENSE, EXPENSE, null));
        assertEquals(CONFLICT, decide(DBBill.STATE_DELETED, "expense-1", EXPENSE,
                EXPENSE.replace("Lunch", "Dinner"), null));
        assertEquals(REMOVE_LOCAL, decide(DBBill.STATE_DELETED, "expense-1", EXPENSE, null, null));
        assertEquals(CONFLICT, decide(DBBill.STATE_EDITED, "expense-1", EXPENSE, null, null));
        assertEquals(REMOVE_LOCAL, decide(DBBill.STATE_DELETED, null, null, null, null));
    }

    @Test
    public void preUpgradeRowsMustPullBaselineBeforeAnyWrite() {
        assertEquals(CONFLICT, decide(DBBill.STATE_EDITED, "expense-1", null, EXPENSE, null));
    }

    @Test
    public void unsupportedDirtyImportsNeverUploadEvenIfQueuedByAStaleScreen() {
        String advanced = EXPENSE.replace("EVENLY", "BY_PERCENTAGE");
        assertEquals(CONFLICT, decide(DBBill.STATE_EDITED, "expense-1", advanced, advanced, null));
        assertEquals(CONFLICT, decide(DBBill.STATE_DELETED, "expense-1", advanced, advanced, null));
    }

    @Test
    public void interruptedCreateIsAcknowledgedOnlyWhenTheSavedFormMatches() {
        String attempt = attempt(true);
        assertEquals(ACKNOWLEDGE, decide(DBBill.STATE_ADDED, null, null, EXPENSE, attempt));
        assertEquals(CONFLICT, decide(DBBill.STATE_ADDED, null, null, null, attempt));
        assertEquals(CONFLICT, decide(DBBill.STATE_ADDED, null, null,
                EXPENSE.replace("Lunch", "Dinner"), attempt));
        // A local delete must not erase an uncertain, possibly successful create.
        assertEquals(CONFLICT, decide(DBBill.STATE_DELETED, null, null, null, attempt));
    }

    @Test
    public void interruptedEditAcknowledgesCommitOrRetriesOnlyAgainstUnchangedBaseline() {
        String attempt = attempt(false);
        assertEquals(ACKNOWLEDGE, decide(DBBill.STATE_EDITED, "expense-1", EXPENSE, EXPENSE, attempt));
        String before = EXPENSE.replace("Lunch", "Before");
        assertEquals(UPDATE, decide(DBBill.STATE_EDITED, "expense-1", before, before, attempt));
        assertEquals(CONFLICT, decide(DBBill.STATE_EDITED, "expense-1", before,
                EXPENSE.replace("Lunch", "Another edit"), attempt));
    }

    @Test
    public void interruptedDeleteCanRetryOrAcknowledgeAbsenceButNotOverwriteAnEdit() {
        String attempt = "{\"delete\":true}";
        assertEquals(DELETE, decide(DBBill.STATE_DELETED, "expense-1", EXPENSE, EXPENSE, attempt));
        assertEquals(REMOVE_LOCAL, decide(DBBill.STATE_DELETED, "expense-1", EXPENSE, null, attempt));
        assertEquals(CONFLICT, decide(DBBill.STATE_DELETED, "expense-1", EXPENSE,
                EXPENSE.replace("Lunch", "Dinner"), attempt));
    }

    @Test
    public void choosingLocalRebasesOnlyAgainstTheVersionTheUserSaw() {
        String chosenRemote = EXPENSE.replace("Lunch", "Dinner");
        assertEquals(UPDATE, decide(DBBill.STATE_EDITED, "expense-1", chosenRemote, chosenRemote, null));
        assertEquals(CONFLICT, decide(DBBill.STATE_EDITED, "expense-1", chosenRemote,
                EXPENSE.replace("Lunch", "Third version"), null));
    }

    private static SpliitSyncDecision.Action decide(int state, String id, String baseline,
                                                   String remote, String attempt) {
        return SpliitSyncDecision.decide(state, id, baseline, remote, attempt);
    }

    private static String attempt(boolean create) {
        JsonObject attempt = new JsonObject();
        attempt.addProperty("id", "expense-1");
        attempt.addProperty("create", create);
        attempt.addProperty("state", create ? DBBill.STATE_ADDED : DBBill.STATE_EDITED);
        attempt.add("form", SpliitExpenseCodec.remoteForm(EXPENSE));
        return attempt.toString();
    }
}
