package net.llego.ihatemoney.persistence;

import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.model.DBMember;
import net.llego.ihatemoney.util.SpliitFixtures;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class SpliitMemberDependencyTest {
    @Test
    public void expenseWaitsForEveryParticipantToExistRemotely() {
        DBBill expense = SpliitFixtures.bill();
        Map<Long, DBMember> members = SpliitFixtures.members();
        assertTrue(SpliitExpenseSync.hasRemoteParticipants(expense, members));
        members.remove(2L);
        assertFalse(SpliitExpenseSync.hasRemoteParticipants(expense, members));
        members = SpliitFixtures.members();
        members.remove(1L);
        assertFalse(SpliitExpenseSync.hasRemoteParticipants(expense, members));
        members = SpliitFixtures.members();
        members.get(2L).setRemoteIdStr(null);
        assertFalse(SpliitExpenseSync.hasRemoteParticipants(expense, members));
        members.get(2L).setRemoteIdStr("");
        assertFalse(SpliitExpenseSync.hasRemoteParticipants(expense, members));
    }
}
