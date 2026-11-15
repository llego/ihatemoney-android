package net.llego.ihatemoney.persistence;

import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.junit.Assert.*;

public class SpliitMemberSchemaTest {
    @Test
    public void discardingPendingLastPayerDoesNotChangeOtherProjects() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement sql = db.createStatement()) {
            sql.execute("CREATE TABLE PROJECTS (ID INTEGER PRIMARY KEY, LASTPAYERID INTEGER)");
            sql.execute("INSERT INTO PROJECTS VALUES (1, 10), (2, 10), (3, 11)");
            try (PreparedStatement clear = db.prepareStatement(SpliitMemberStore.CLEAR_LAST_PAYER)) {
                clear.setLong(1, 1);
                clear.setLong(2, 10);
                assertEquals(1, clear.executeUpdate());
            }
            try (ResultSet rows = sql.executeQuery("SELECT LASTPAYERID FROM PROJECTS ORDER BY ID")) {
                assertTrue(rows.next()); assertNull(rows.getObject(1));
                assertTrue(rows.next()); assertEquals(10, rows.getInt(1));
                assertTrue(rows.next()); assertEquals(11, rows.getInt(1));
            }
        }
    }
    @Test
    public void linkingParticipantsPreservesSplitsWithoutDuplicateOwers() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement sql = db.createStatement()) {
            sql.execute("CREATE TABLE BILLOWERS (BILLID INTEGER, MEMBERID INTEGER)");
            sql.execute("INSERT INTO BILLOWERS VALUES (1, 10), (1, 20), (2, 20), (3, 30)");
            try (PreparedStatement remove = db.prepareStatement(SpliitMemberStore.REMOVE_DUPLICATE_OWERS)) {
                remove.setLong(1, 20);
                remove.setLong(2, 10);
                remove.executeUpdate();
            }
            sql.execute("UPDATE BILLOWERS SET MEMBERID = 10 WHERE MEMBERID = 20");
            try (ResultSet rows = sql.executeQuery("SELECT BILLID, MEMBERID FROM BILLOWERS ORDER BY BILLID")) {
                assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); assertEquals(10, rows.getInt(2));
                assertTrue(rows.next()); assertEquals(2, rows.getInt(1)); assertEquals(10, rows.getInt(2));
                assertTrue(rows.next()); assertEquals(3, rows.getInt(1)); assertEquals(30, rows.getInt(2));
                assertFalse(rows.next());
            }
        }
    }
    @Test
    public void upgradeKeepsExistingMembersAndInitializesSyncState() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement sql = db.createStatement()) {
            sql.execute("CREATE TABLE MEMBERS (ID INTEGER PRIMARY KEY, REMOTEIDSTR TEXT, NAME TEXT, STATE INTEGER)");
            sql.execute("INSERT INTO MEMBERS VALUES (1, 'alice', 'Alice', 0), (2, NULL, 'Bob', 1)");
            for (String migration : SpliitMemberStore.MIGRATIONS) sql.execute(migration);
            try (ResultSet rows = sql.executeQuery("SELECT * FROM MEMBERS ORDER BY ID")) {
                assertTrue(rows.next());
                assertEquals("alice", rows.getString("REMOTEIDSTR"));
                assertEquals("Alice", rows.getString("NAME"));
                assertNull(rows.getString(SpliitMemberStore.BASELINE));
                assertTrue(rows.next());
                assertEquals(1, rows.getInt("STATE"));
                assertNull(rows.getString(SpliitMemberStore.ATTEMPT));
                assertNull(rows.getString(SpliitMemberStore.CONFLICT));
            }
        }
    }
}
