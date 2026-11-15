package net.llego.ihatemoney.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class SpliitExpensePaginationTest {
    @Test
    public void enumeratesEveryExpenseWhenTiedRowsChangeOrderBetweenOffsetQueries() {
        List<JsonObject> rows = expenses(235, 15);
        SpliitExpensePagination pagination = new SpliitExpensePagination();
        int requests = 0;
        while (!pagination.isComplete()) {
            int cursor = pagination.getCursor();
            pagination.accept(page(reshuffleTies(rows, requests++), cursor, 100));
            assertTrue("Pagination must make progress", requests < 10);
        }
        assertEquals(235, pagination.getExpenseIds().size());
        for (int id = 0; id < 235; id++) assertTrue(pagination.getExpenseIds().contains("expense-" + id));
    }

    @Test
    public void rereadsTheWholeTrailingTimestampBucket() {
        SpliitExpensePagination pagination = new SpliitExpensePagination();
        pagination.accept(page(expenses(200, 15), 0, 100));
        assertEquals(90, pagination.getCursor());
    }

    @Test
    public void supportsEmptyGroupsAndSingleFinalPageWithIdenticalTimestamps() {
        SpliitExpensePagination empty = new SpliitExpensePagination();
        empty.accept(page(expenses(0, 1), 0, 100));
        assertTrue(empty.isComplete());
        assertTrue(empty.getExpenseIds().isEmpty());
        SpliitExpensePagination tied = new SpliitExpensePagination();
        tied.accept(page(expenses(100, 100), 0, 100));
        assertEquals(100, tied.getExpenseIds().size());
    }

    @Test
    public void handlesUniqueTimestampsAndNormalizesEquivalentDateRepresentations() {
        List<JsonObject> rows = expenses(101, 1);
        SpliitExpensePagination pagination = new SpliitExpensePagination();
        pagination.accept(page(rows, 0, 100));
        assertEquals(99, pagination.getCursor());
        pagination.accept(page(rows, 99, 100));
        assertEquals(101, pagination.getExpenseIds().size());
    }

    @Test(expected = IllegalStateException.class)
    public void refusesToExposeAnIncompleteList() {
        new SpliitExpensePagination().getExpenseIds();
    }

    @Test(expected = IllegalStateException.class)
    public void refusesBucketsLargerThanTheServersPageLimit() {
        new SpliitExpensePagination().accept(page(expenses(101, 101), 0, 100));
    }

    @Test(expected = IllegalStateException.class)
    public void refusesNonAdvancingCursors() {
        JsonObject page = page(expenses(200, 1), 0, 100);
        page.addProperty("nextCursor", 0);
        new SpliitExpensePagination().accept(page);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDuplicateRowsInsideASinglePage() {
        List<JsonObject> rows = expenses(2, 1);
        rows.set(1, rows.get(0));
        new SpliitExpensePagination().accept(page(rows, 0, 100));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsAnOverlappingExpenseThatChangedDuringTheScan() {
        List<JsonObject> rows = expenses(101, 1);
        SpliitExpensePagination pagination = new SpliitExpensePagination();
        pagination.accept(page(rows, 0, 100));
        rows.get(99).addProperty("amount", 999);
        pagination.accept(page(rows, 99, 100));
    }

    @Test(expected = IllegalStateException.class)
    public void refusesACompletedScanWithMissingIds() {
        List<JsonObject> rows = expenses(101, 1);
        SpliitExpensePagination pagination = new SpliitExpensePagination();
        pagination.accept(page(rows, 0, 100));
        rows.set(100, rows.get(98));
        pagination.accept(page(rows, 99, 100));
    }

    private static List<JsonObject> expenses(int count, int bucketSize) {
        List<JsonObject> result = new ArrayList<>();
        for (int id = 0; id < count; id++) {
            JsonObject row = new JsonObject();
            row.addProperty("id", "expense-" + id);
            row.addProperty("expenseDate", "2026-11-14T00:00:00.000Z");
            row.addProperty("createdAt", Instant.ofEpochSecond(100000 - id / bucketSize).toString());
            row.addProperty("amount", 100);
            result.add(row);
        }
        return result;
    }

    private static List<JsonObject> reshuffleTies(List<JsonObject> rows, int request) {
        List<JsonObject> result = new ArrayList<>();
        int start = 0;
        while (start < rows.size()) {
            int end = start + 1;
            while (end < rows.size() && rows.get(start).get("createdAt").equals(rows.get(end).get("createdAt"))) end++;
            List<JsonObject> bucket = new ArrayList<>(rows.subList(start, end));
            Collections.rotate(bucket, request * 7);
            result.addAll(bucket);
            start = end;
        }
        return result;
    }

    private static JsonObject page(List<JsonObject> all, int cursor, int limit) {
        JsonArray rows = new JsonArray();
        for (int index = cursor; index < Math.min(all.size(), cursor + limit); index++) rows.add(all.get(index));
        JsonObject page = new JsonObject();
        page.add("expenses", rows);
        page.addProperty("hasMore", cursor + limit < all.size());
        page.addProperty("nextCursor", cursor + limit);
        return page;
    }
}
