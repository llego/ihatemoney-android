package net.eneiluj.moneybuster.persistence;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BillQueriesTest {
    private Connection db;

    @Before
    public void setUp() throws Exception {
        db = DriverManager.getConnection("jdbc:sqlite::memory:");
        execute("CREATE TABLE BILLS (ID INTEGER PRIMARY KEY, PROJECTID INTEGER, STATE INTEGER, WHAT TEXT)");
        execute("CREATE TABLE BILLOWERS (ID INTEGER PRIMARY KEY, BILLID INTEGER, MEMBERID INTEGER)");
        execute("CREATE TABLE MEMBERS (ID INTEGER PRIMARY KEY, PROJECTID INTEGER, REMOTEID INTEGER)");
        execute("INSERT INTO BILLS VALUES (1, 10, 0, 'Lunch'), (2, 10, 0, 'Train'), "
                + "(3, 20, 0, 'Lunch'), (4, 10, 3, 'Deleted')");
        execute("INSERT INTO BILLOWERS VALUES (1, 1, 100), (2, 1, 101), (3, 2, 101), "
                + "(4, 3, 200), (5, 4, 100)");
    }

    @After
    public void tearDown() throws Exception {
        db.close();
    }

    @Test
    public void loadsAllSelectedParticipantsWithoutLeakingOtherProjectsOrDeletedExpenses() throws Exception {
        assertEquals(Arrays.asList("1:100", "1:101", "2:101"),
                participants("PROJECTID = ? AND STATE != ?", 10, 3));
    }

    @Test
    public void preservesBoundTextSearchArguments() throws Exception {
        assertEquals(Arrays.asList("1:100", "1:101"),
                participants("PROJECTID = ? AND STATE != ? AND WHAT LIKE ?", 10, 3, "%Lunch%"));
        assertTrue(participants("PROJECTID = ? AND WHAT LIKE ?", 10, "%doesn't exist%").isEmpty());
    }

    @Test
    public void preservesParticipantSearchSubqueries() throws Exception {
        String selection = "PROJECTID = ? AND STATE != ? AND (WHAT LIKE ? OR ID IN "
                + "(SELECT BILLS.ID FROM BILLS INNER JOIN BILLOWERS "
                + "WHERE MEMBERID = ? AND BILLS.ID = BILLOWERS.BILLID))";
        assertEquals(Arrays.asList("1:100", "1:101", "2:101"),
                participants(selection, 10, 3, "%nothing%", 101));
    }

    @Test
    public void supportsEmptySelectionAndNoMatches() throws Exception {
        assertEquals(5, participants("").size());
        assertTrue(participants("PROJECTID = ?", 999).isEmpty());
    }

    @Test
    public void doesNotNeedOneSqlParameterPerExpense() throws Exception {
        db.setAutoCommit(false);
        try (PreparedStatement bills = db.prepareStatement("INSERT INTO BILLS VALUES (?, 30, 0, 'Expense')");
             PreparedStatement owers = db.prepareStatement("INSERT INTO BILLOWERS VALUES (?, ?, 300)")) {
            for (int id = 1000; id < 2500; id++) {
                bills.setInt(1, id);
                bills.executeUpdate();
                owers.setInt(1, id);
                owers.setInt(2, id);
                owers.executeUpdate();
            }
        }
        db.commit();
        db.setAutoCommit(true);
        assertEquals(1500, participants("PROJECTID = ?", 30).size());
    }

    @Test
    public void dateRangeExcludesDeletedExpensesAndOtherProjects() throws Exception {
        execute("ALTER TABLE BILLS ADD COLUMN TIMESTAMP INTEGER");
        execute("UPDATE BILLS SET TIMESTAMP = CASE ID WHEN 1 THEN 100 WHEN 2 THEN 200 "
                + "WHEN 3 THEN 50 WHEN 4 THEN 300 END");
        assertArrayEquals(new long[]{100, 200}, dateRange(10));
    }

    @Test
    public void dateRangeIsAbsentWhenThereAreNoActiveExpenses() throws Exception {
        execute("ALTER TABLE BILLS ADD COLUMN TIMESTAMP INTEGER DEFAULT 100");
        assertNull(dateRange(999));
        execute("UPDATE BILLS SET STATE = 3 WHERE PROJECTID = 10");
        assertNull(dateRange(10));
    }

    @Test
    public void singleExpenseProvidesBothDateBounds() throws Exception {
        execute("ALTER TABLE BILLS ADD COLUMN TIMESTAMP INTEGER DEFAULT 100");
        assertArrayEquals(new long[]{100, 100}, dateRange(20));
    }

    @Test
    public void epochTimestampIsNotConfusedWithAnEmptyProject() throws Exception {
        execute("ALTER TABLE BILLS ADD COLUMN TIMESTAMP INTEGER DEFAULT 0");
        assertArrayEquals(new long[]{0, 0}, dateRange(10));
    }

    @Test
    public void migrationIndexesAreIdempotentAndKeepExistingData() throws Exception {
        List<String> before = participants("");
        for (int run = 0; run < 2; run++) {
            for (String sql : BillQueries.SYNC_INDEXES) execute(sql);
        }
        assertEquals(before, participants(""));
        try (Statement statement = db.createStatement();
             ResultSet rows = statement.executeQuery("EXPLAIN QUERY PLAN SELECT * FROM BILLOWERS WHERE BILLID = 1")) {
            assertTrue(rows.next());
            assertTrue(rows.getString(4).contains("BILLOWERS_BILLID_idx"));
        }
    }

    private List<String> participants(String selection, Object... args) throws Exception {
        List<String> result = new ArrayList<>();
        try (PreparedStatement statement = db.prepareStatement("SELECT BILLID, MEMBERID FROM BILLOWERS WHERE "
                + BillQueries.participantsForSelection(selection) + " ORDER BY BILLID, MEMBERID")) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(rows.getLong(1) + ":" + rows.getLong(2));
            }
        }
        return result;
    }

    private long[] dateRange(long projectId) throws Exception {
        try (PreparedStatement statement = db.prepareStatement(BillQueries.DATE_RANGE)) {
            statement.setLong(1, projectId);
            statement.setInt(2, 3);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || rows.getObject(1) == null || rows.getObject(2) == null) return null;
                return new long[]{rows.getLong(1), rows.getLong(2)};
            }
        }
    }

    private void execute(String sql) throws Exception {
        try (Statement statement = db.createStatement()) {
            statement.execute(sql);
        }
    }
}
