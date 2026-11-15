package net.llego.ihatemoney.persistence;

import org.junit.Test;
import net.llego.ihatemoney.model.DBBill;

import java.util.Map;

import static net.llego.ihatemoney.persistence.SpliitMemberDecision.Action.*;
import static org.junit.Assert.*;

public class SpliitMemberDecisionTest {
    @Test
    public void failedRenameVerificationPreservesTheObservedRemoteVersion() {
        com.google.gson.JsonObject conflict = com.google.gson.JsonParser.parseString(
                SpliitMemberSync.conflictAfterWrite(false, "Server name")).getAsJsonObject();
        assertEquals("Server name", conflict.get("name").getAsString());
        assertTrue(com.google.gson.JsonParser.parseString(
                SpliitMemberSync.conflictAfterWrite(false, null)).getAsJsonObject().get("name").isJsonNull());
        assertEquals("null", SpliitMemberSync.conflictAfterWrite(true, "Server name"));
    }
    @Test
    public void offlineParticipantCanBeRenamedUntilAWriteIsClaimed() {
        assertEquals(DBBill.STATE_ADDED,
                SpliitMemberDecision.renamedState(DBBill.STATE_ADDED, null, null, null, null));
        assertEquals(-1, SpliitMemberDecision.renamedState(DBBill.STATE_ADDED, null, null, "attempt", null));
        assertEquals(-1, SpliitMemberDecision.renamedState(DBBill.STATE_ADDED, null, null, null, "null"));
        assertEquals(DBBill.STATE_EDITED, SpliitMemberDecision.renamedState(DBBill.STATE_OK, "remote", "Alice", null, null));
        assertEquals(-1, SpliitMemberDecision.renamedState(DBBill.STATE_OK, "remote", null, null, null));
        assertEquals(-1, SpliitMemberDecision.renamedState(DBBill.STATE_DELETED, "remote", "Alice", null, null));
    }
    @Test
    public void renameDoesNotOverwriteAnotherEditOrDeletion() {
        assertEquals(UPDATE, SpliitMemberDecision.rename("Alice", "Alicia", "Alice"));
        assertEquals(ACKNOWLEDGE, SpliitMemberDecision.rename("Alice", "Alicia", "Alicia"));
        assertEquals(CONFLICT, SpliitMemberDecision.rename("Alice", "Alicia", "Alison"));
        assertEquals(CONFLICT, SpliitMemberDecision.rename("Alice", "Alicia", null));
        assertEquals(CONFLICT, SpliitMemberDecision.rename(null, "Alicia", "Alice"));
    }

    @Test
    public void uncertainCreateIsNeverBlindlyRetried() {
        Map<String, String> before = Map.of("a", "Alice");
        assertEquals(CREATE, SpliitMemberDecision.create("Bob", before, false));
        assertEquals(CONFLICT, SpliitMemberDecision.create("Alice", before, false));
        assertEquals(CONFLICT, SpliitMemberDecision.create("Bob", before, true));
        Map<String, String> after = Map.of("a", "Alice", "b", "Bob");
        assertEquals(CONFLICT, SpliitMemberDecision.create("Bob", after, true));
    }
}
