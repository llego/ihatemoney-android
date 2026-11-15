package net.llego.ihatemoney.persistence;

import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static net.llego.ihatemoney.util.SpliitFixtures.EXPENSE;
import static org.junit.Assert.*;

public class SpliitSyncSchemaTest {
    @Test
    public void upgradeIsAdditiveAndKeepsExistingBackendIdsAndPendingChanges() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement sql = db.createStatement()) {
            sql.execute("CREATE TABLE BILLS (ID INTEGER PRIMARY KEY, REMOTEID INTEGER, REMOTEIDSTR TEXT, STATE INTEGER)");
            sql.execute("INSERT INTO BILLS VALUES (1, 42, NULL, 2), (2, 0, 'spliit-id', 3)");
            for (String migration : SpliitSyncStore.MIGRATIONS) sql.execute(migration);
            try (ResultSet rows = sql.executeQuery("SELECT * FROM BILLS ORDER BY ID")) {
                assertTrue(rows.next());
                assertEquals(42, rows.getLong("REMOTEID"));
                assertEquals(2, rows.getInt("STATE"));
                assertNull(rows.getString(SpliitSyncStore.BASELINE));
                assertTrue(rows.next());
                assertEquals("spliit-id", rows.getString("REMOTEIDSTR"));
                assertEquals(3, rows.getInt("STATE"));
                assertNull(rows.getString(SpliitSyncStore.CONFLICT));
                assertNull(rows.getString(SpliitSyncStore.ATTEMPT));
            }
        }
    }

    @Test
    public void freshSchemaStoresFullMetadataAndDistinguishesDeletionFromNoConflict() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement sql = db.createStatement()) {
            sql.execute("CREATE TABLE BILLS (ID INTEGER PRIMARY KEY, " + SpliitSyncStore.COLUMNS + ")");
            try (PreparedStatement insert = db.prepareStatement("INSERT INTO BILLS VALUES (1, ?, ?, ?)")) {
                insert.setString(1, EXPENSE);
                insert.setString(2, "null");
                insert.setString(3, "{\"create\":true,\"id\":\"pending-id\"}");
                insert.executeUpdate();
            }
            try (ResultSet rows = sql.executeQuery("SELECT * FROM BILLS")) {
                assertTrue(rows.next());
                assertEquals(EXPENSE, rows.getString(SpliitSyncStore.BASELINE));
                assertEquals("null", rows.getString(SpliitSyncStore.CONFLICT));
                assertTrue(rows.getString(SpliitSyncStore.ATTEMPT).contains("pending-id"));
            }
        }
    }
}
