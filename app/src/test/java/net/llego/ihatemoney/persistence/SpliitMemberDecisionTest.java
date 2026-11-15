package net.llego.ihatemoney.persistence;

import org.junit.Test;

import java.util.Map;

import static net.llego.ihatemoney.persistence.SpliitMemberDecision.Action.*;
import static org.junit.Assert.*;

public class SpliitMemberDecisionTest {
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
