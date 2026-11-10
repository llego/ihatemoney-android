package net.llego.ihatemoney.persistence;

import net.llego.ihatemoney.model.ProjectType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class BillListOrderTest {
    private Connection db;

    @Before
    public void setUp() throws Exception {
        db = DriverManager.getConnection("jdbc:sqlite::memory:");
        execute("CREATE TABLE BILLS (ID INTEGER PRIMARY KEY, REMOTEID INTEGER, "
                + "TIMESTAMP INTEGER, CREATIONDATE TEXT NOT NULL DEFAULT '')");
    }

    @After
    public void tearDown() throws Exception {
        db.close();
    }

    @Test
    public void sameDayUsesServerIdsNotImportOrder() throws Exception {
        bill(1, 10, "2026-11-09T00:00", "2026-11-09");
        bill(20, 2, "2026-11-09T00:00", "2026-11-09");
        bill(7, 100, "2026-11-09T00:00", "2026-11-09");
        assertEquals(Arrays.asList(7L, 1L, 20L), ordered(ProjectType.IHATEMONEY));
    }

    @Test
    public void expenseDateTakesPriorityOverCreationDateAndId() throws Exception {
        bill(1, 100, "2026-11-08T23:59", "2026-11-10");
        bill(2, 2, "2026-11-09T00:00", "2026-11-01");
        assertEquals(Arrays.asList(2L, 1L), ordered(ProjectType.IHATEMONEY));
    }

    @Test
    public void sameExpenseDateUsesCreationDateBeforeServerId() throws Exception {
        bill(1, 100, "2026-11-09T00:00", "2026-11-09");
        bill(2, 2, "2026-11-09T00:00", "2026-11-10");
        assertEquals(Arrays.asList(2L, 1L), ordered(ProjectType.IHATEMONEY));
    }

    @Test
    public void ignoredTimeOfDayDoesNotOverrideServerOrdering() throws Exception {
        bill(1, 10, "2026-11-09T20:00", "2026-11-09");
        bill(2, 11, "2026-11-09T00:00", "2026-11-09");
        assertEquals(Arrays.asList(2L, 1L), ordered(ProjectType.IHATEMONEY));
    }

    @Test
    public void unsyncedBillsAreNewestFirstWithinTheirDay() throws Exception {
        bill(1, 500, "2026-11-09T00:00", "2026-11-09");
        bill(2, 0, "2026-11-09T20:00", "2026-11-09");
        bill(3, 0, "2026-11-09T08:00", "2026-11-09");
        bill(4, 0, "2026-11-08T23:59", "2026-11-09");
        assertEquals(Arrays.asList(3L, 2L, 1L, 4L), ordered(ProjectType.IHATEMONEY));
    }

    @Test
    public void newExpenseKeepsItsPlaceAfterSyncRemovesTimeOfDay() throws Exception {
        bill(1, 50, "2026-11-09T00:00", "2026-11-09");
        bill(2, 0, "2026-11-09T15:30", "2026-11-09");
        List<Long> before = ordered(ProjectType.IHATEMONEY);
        execute("UPDATE BILLS SET REMOTEID = 51, TIMESTAMP = "
                + timestamp("2026-11-09T00:00") + " WHERE ID = 2");
        assertEquals(Arrays.asList(2L, 1L), before);
        assertEquals(before, ordered(ProjectType.IHATEMONEY));
    }

    @Test
    public void missingCreationDatesFallBackToServerId() throws Exception {
        bill(1, 50, "2026-11-09T00:00", "");
        bill(2, 51, "2026-11-09T00:00", "");
        assertEquals(Arrays.asList(2L, 1L), ordered(ProjectType.IHATEMONEY));
    }

    @Test
    public void nextSyncBackfillsCreationDatesForUnchangedCachedExpenses() throws Exception {
        bill(1, 100, "2026-11-09T00:00", "");
        bill(2, 2, "2026-11-09T00:00", "");
        assertEquals(Arrays.asList(1L, 2L), ordered(ProjectType.IHATEMONEY));
        execute("UPDATE BILLS SET CREATIONDATE = '2026-11-09' WHERE ID = 1");
        execute("UPDATE BILLS SET CREATIONDATE = '2026-11-10' WHERE ID = 2");
        assertEquals(Arrays.asList(2L, 1L), ordered(ProjectType.IHATEMONEY));
    }

    @Test
    public void editingExpenseDateMovesItToTheCorrectDay() throws Exception {
        bill(1, 50, "2026-11-08T00:00", "2026-11-09");
        bill(2, 51, "2026-11-09T00:00", "2026-11-09");
        execute("UPDATE BILLS SET TIMESTAMP = " + timestamp("2026-11-10T00:00") + " WHERE ID = 1");
        assertEquals(Arrays.asList(1L, 2L), ordered(ProjectType.IHATEMONEY));
    }

    @Test
    public void otherBackendsKeepTimeBasedOrdering() throws Exception {
        bill(1, 10, "2026-11-09T20:00", "2026-11-08");
        bill(2, 11, "2026-11-09T00:00", "2026-11-09");
        for (ProjectType type : new ProjectType[]{ProjectType.LOCAL, null}) {
            assertEquals(Arrays.asList(1L, 2L), ordered(type));
        }
    }

    private void bill(long id, long remoteId, String dateTime, String creationDate) throws Exception {
        try (PreparedStatement statement = db.prepareStatement("INSERT INTO BILLS VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, id);
            statement.setLong(2, remoteId);
            statement.setLong(3, timestamp(dateTime));
            statement.setString(4, creationDate);
            statement.executeUpdate();
        }
    }

    private long timestamp(String dateTime) {
        return LocalDateTime.parse(dateTime).atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    private void execute(String sql) throws Exception {
        try (Statement statement = db.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<Long> ordered(ProjectType type) throws Exception {
        List<Long> ids = new ArrayList<>();
        try (Statement statement = db.createStatement();
             ResultSet rows = statement.executeQuery("SELECT ID FROM BILLS ORDER BY " + BillListOrder.forProject(type))) {
            while (rows.next()) ids.add(rows.getLong(1));
        }
        return ids;
    }
}
