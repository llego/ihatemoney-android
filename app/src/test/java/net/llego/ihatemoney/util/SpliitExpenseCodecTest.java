package net.llego.ihatemoney.util;

import com.google.gson.JsonObject;

import net.llego.ihatemoney.model.DBBill;

import org.junit.Test;

import static net.llego.ihatemoney.util.SpliitFixtures.EXPENSE;
import static org.junit.Assert.*;

public class SpliitExpenseCodecTest {
    @Test
    public void roundTripKeepsReceiptCategoryReimbursementAndNotes() {
        JsonObject raw = SpliitExpenseCodec.parse(EXPENSE);
        JsonObject form = SpliitExpenseCodec.localForm(SpliitFixtures.bill(), EXPENSE, SpliitFixtures.members());
        assertEquals(raw.get("documents"), form.get("documents"));
        assertEquals(7, form.get("category").getAsInt());
        assertTrue(form.get("isReimbursement").getAsBoolean());
        assertEquals("Keep receipt", form.get("notes").getAsString());
        assertEquals(1250, form.get("amount").getAsLong());
        assertEquals(SpliitExpenseCodec.remoteForm(EXPENSE), form);
        assertFalse(form.has("originalAmount"));
        assertFalse(form.has("conversionRate"));
        assertFalse(form.get("saveDefaultSplittingOptions").getAsBoolean());
    }

    @Test
    public void editingDoesNotMutateBaselineMetadata() {
        DBBill bill = SpliitFixtures.bill();
        bill.setWhat("Dinner");
        JsonObject form = SpliitExpenseCodec.localForm(bill, EXPENSE, SpliitFixtures.members());
        assertEquals("Dinner", form.get("title").getAsString());
        assertEquals("Lunch", SpliitExpenseCodec.parse(EXPENSE).get("title").getAsString());
        assertEquals(SpliitExpenseCodec.parse(EXPENSE).get("documents"), form.get("documents"));
    }

    @Test
    public void centsAreRoundedWithoutBinaryFloatingPointTruncation() {
        DBBill bill = SpliitFixtures.bill();
        bill.setAmount(10.075);
        assertEquals(1008, SpliitExpenseCodec.localForm(bill, null, SpliitFixtures.members())
                .get("amount").getAsLong());
    }

    @Test
    public void preservesUneditedDatePrecisionAndTreatsNullNotesAsEmpty() {
        String baseline = EXPENSE.replace("12:00:00.000Z", "12:00:00.123Z")
                .replace("\"notes\":\"Keep receipt\"", "\"notes\":null");
        JsonObject form = SpliitExpenseCodec.localForm(SpliitFixtures.bill(), baseline, SpliitFixtures.members());
        assertEquals("2026-11-14T12:00:00.123Z", form.get("expenseDate").getAsString());
        assertEquals("", SpliitExpenseCodec.remoteForm(baseline).get("notes").getAsString());
    }

    @Test
    public void newExpenseUsesSafeDefaultsInsteadOfAnotherBackendsCategory() {
        JsonObject form = SpliitExpenseCodec.localForm(SpliitFixtures.bill(), null, SpliitFixtures.members());
        assertEquals(0, form.get("category").getAsInt());
        assertFalse(form.get("isReimbursement").getAsBoolean());
        assertEquals(0, form.getAsJsonArray("documents").size());
        assertEquals("NONE", form.get("recurrenceRule").getAsString());
    }

    @Test
    public void advancedTypesAndIncompleteImportsAreReadOnly() {
        assertTrue(SpliitExpenseCodec.editable(EXPENSE));
        assertFalse(SpliitExpenseCodec.editable(null));
        for (String mode : new String[]{"BY_SHARES", "BY_AMOUNT", "BY_PERCENTAGE"}) {
            assertFalse(SpliitExpenseCodec.editable(EXPENSE.replace("EVENLY", mode)));
        }
        assertFalse(SpliitExpenseCodec.editable(EXPENSE.replace("\"NONE\"", "\"MONTHLY\"")));
        assertFalse(SpliitExpenseCodec.editable(EXPENSE.replace("\"originalCurrency\":null", "\"originalCurrency\":\"EUR\"")));
        JsonObject incomplete = SpliitExpenseCodec.parse(EXPENSE);
        incomplete.remove("documents");
        assertFalse(SpliitExpenseCodec.editable(incomplete.toString()));
    }

    @Test
    public void snapshotComparisonIgnoresObjectKeyOrderButDetectsMetadataChanges() {
        assertTrue(SpliitExpenseCodec.same("{\"a\":1,\"b\":2}", "{\"b\":2,\"a\":1}"));
        assertFalse(SpliitExpenseCodec.same(EXPENSE, EXPENSE.replace("receipt.jpg", "other.jpg")));
        assertFalse(SpliitExpenseCodec.same(EXPENSE, null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesToFlattenAnAdvancedSplit() {
        SpliitExpenseCodec.localForm(SpliitFixtures.bill(), EXPENSE.replace("EVENLY", "BY_AMOUNT"),
                SpliitFixtures.members());
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesUnknownParticipants() {
        SpliitExpenseCodec.localForm(SpliitFixtures.bill(), null, java.util.Collections.emptyMap());
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesZeroAfterRoundingToCents() {
        DBBill bill = SpliitFixtures.bill();
        bill.setAmount(0.001);
        SpliitExpenseCodec.localForm(bill, null, SpliitFixtures.members());
    }
}
