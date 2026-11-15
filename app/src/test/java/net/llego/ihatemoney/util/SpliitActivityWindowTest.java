package net.llego.ihatemoney.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class SpliitActivityWindowTest {
    @Test
    public void includesAllActivitiesAtMarkerTimeBeforeStopping() {
        SpliitActivityWindow window = new SpliitActivityWindow("marker");
        window.accept(SpliitExpenseCodec.parse("""
                {"activities":[{"id":"new","time":"2026-11-15T12:00:01Z","activityType":"UPDATE_EXPENSE","expenseId":"a"},
                {"id":"marker","time":"2026-11-15T12:00:00Z","activityType":"UPDATE_EXPENSE","expenseId":"b"}],"hasMore":true,"nextCursor":2}
                """));
        assertFalse(window.complete());
        window.accept(SpliitExpenseCodec.parse("""
                {"activities":[{"id":"tied","time":"2026-11-15T12:00:00Z","activityType":"UPDATE_EXPENSE","expenseId":"c"},
                {"id":"old","time":"2026-11-15T11:59:59Z","activityType":"UPDATE_GROUP"}],"hasMore":false,"nextCursor":4}
                """));
        assertTrue(window.usable());
        assertEquals("new", window.newest());
        assertTrue(window.changed().contains("c"));
    }

    @Test
    public void missingMarkerOrUnknownActivityFallsBack() {
        SpliitActivityWindow missing = new SpliitActivityWindow("missing");
        missing.accept(SpliitExpenseCodec.parse("{\"activities\":[],\"hasMore\":false,\"nextCursor\":0}"));
        assertFalse(missing.usable());
        SpliitActivityWindow unknown = new SpliitActivityWindow("old");
        unknown.accept(SpliitExpenseCodec.parse("""
                {"activities":[{"id":"x","time":"2026-11-15T12:00:00Z","activityType":"OTHER"}],"hasMore":false}
                """));
        assertFalse(unknown.usable());
    }
}
