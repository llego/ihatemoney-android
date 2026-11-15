package net.llego.ihatemoney.persistence;

import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.*;

public class SpliitMemberSchemaTest {
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
