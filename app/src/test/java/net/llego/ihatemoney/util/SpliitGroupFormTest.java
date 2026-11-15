package net.llego.ihatemoney.util;

import com.google.gson.JsonObject;

import org.junit.Test;

import static org.junit.Assert.*;

public class SpliitGroupFormTest {
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
}
