package net.llego.ihatemoney.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import org.junit.Test;

import static org.junit.Assert.*;

public class SpliitGroupFormTest {
    private static final String GROUP = """
            {"name":"Trip","information":"notes","currency":"€","currencyCode":"EUR",
             "participants":[{"id":"a","name":"Alice"},{"id":"b","name":"Bob"}]}
            """;

    @Test
    public void preservesSettingsAndParticipantIds() {
        JsonObject form = SpliitGroupForm.fromSnapshot("""
                {"name":"Trip","information":"notes","currency":"€","currencyCode":"EUR",
                 "participants":[{"id":"a","name":"Alice","groupId":"g"}]}
                """);
        assertEquals("notes", form.get("information").getAsString());
        assertEquals("EUR", form.get("currencyCode").getAsString());
        JsonObject participant = form.getAsJsonArray("participants").get(0).getAsJsonObject();
        assertEquals("a", participant.get("id").getAsString());
        assertFalse(participant.has("groupId"));
    }

    @Test
    public void nullInformationUsesTheWebFormsEmptyString() {
        JsonObject form = SpliitGroupForm.fromSnapshot("""
                {"name":"Trip","information":null,"currency":"$","currencyCode":null,
                 "participants":[]}
                """);
        assertEquals("", form.get("information").getAsString());
        assertTrue(form.get("currencyCode").isJsonNull());
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesSnapshotsMissingSettingsInsteadOfOverwritingThem() {
        SpliitGroupForm.fromSnapshot("{\"name\":\"Trip\",\"participants\":[]}");
    }

    @Test
    public void additionAndRenamePreserveOtherParticipantsAndSettings() {
        JsonObject added = SpliitGroupForm.add(GROUP, "Clara");
        assertEquals(3, added.getAsJsonArray("participants").size());
        assertFalse(added.getAsJsonArray("participants").get(2).getAsJsonObject().has("id"));
        JsonObject renamed = SpliitGroupForm.rename(GROUP, "a", "Alicia");
        assertEquals("Alicia", renamed.getAsJsonArray("participants").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("Bob", renamed.getAsJsonArray("participants").get(1).getAsJsonObject().get("name").getAsString());
        assertEquals("notes", renamed.get("information").getAsString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesDuplicateNames() { SpliitGroupForm.add(GROUP, "Alice"); }

    @Test(expected = IllegalArgumentException.class)
    public void refusesWhitespaceVariantOfExistingName() { SpliitGroupForm.add(GROUP, "Alice "); }

    @Test(expected = IllegalArgumentException.class)
    public void refusesRenamingDeletedMember() { SpliitGroupForm.rename(GROUP, "missing", "Name"); }

    @Test(expected = IllegalArgumentException.class)
    public void refusesShortNames() { SpliitGroupForm.add(GROUP, "A"); }

    @Test
    public void rejectsLocalValidationErrorsWithoutClassifyingMalformedSnapshotsAsUserErrors() {
        assertThrows(SpliitGroupForm.MemberValidationException.class, () -> SpliitGroupForm.add(GROUP, "A "));
        assertThrows(SpliitGroupForm.MemberValidationException.class, () -> SpliitGroupForm.add(GROUP, "Alice"));
        IllegalArgumentException malformed = assertThrows(IllegalArgumentException.class,
                () -> SpliitGroupForm.add("{\"name\":\"Trip\",\"participants\":[]}", "Valid"));
        assertFalse(malformed instanceof SpliitGroupForm.MemberValidationException);
        assertEquals("Valid", SpliitGroupForm.add(GROUP, "Valid")
                .getAsJsonArray("participants").get(2).getAsJsonObject().get("name").getAsString());
    }

    @Test
    public void fullGroupIsEditableValidationErrorButRenamesStillWork() {
        JsonObject group = SpliitExpenseCodec.parse(GROUP);
        JsonArray participants = new JsonArray();
        for (int index = 0; index < 100; index++) {
            JsonObject participant = new JsonObject();
            participant.addProperty("id", "id" + index);
            participant.addProperty("name", "Person " + index);
            participants.add(participant);
        }
        group.add("participants", participants);
        assertThrows(SpliitGroupForm.MemberValidationException.class,
                () -> SpliitGroupForm.add(group.toString(), "New person"));
        assertEquals("Renamed", SpliitGroupForm.rename(group.toString(), "id0", "Renamed")
                .getAsJsonArray("participants").get(0).getAsJsonObject().get("name").getAsString());
    }
}
