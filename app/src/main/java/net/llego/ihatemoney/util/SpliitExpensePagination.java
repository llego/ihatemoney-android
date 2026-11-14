package net.llego.ihatemoney.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spliit orders by expenseDate/createdAt without an ID tie-breaker. Offset pages
 * can therefore omit records at tied boundaries. Re-read the entire trailing
 * timestamp bucket rather than advancing into its nondeterministically sorted rows.
 */
public final class SpliitExpensePagination {
    private final Map<String, JsonObject> expenses = new LinkedHashMap<>();
    private int cursor;
    private boolean complete;

    public int getCursor() { return cursor; }
    public boolean isComplete() { return complete; }

    public void accept(JsonObject page) {
        if (complete) throw new IllegalStateException("Spliit pagination already completed");
        JsonArray rows = page.getAsJsonArray("expenses");
        boolean hasMore = page.get("hasMore").getAsBoolean();
        Set<String> pageIds = new HashSet<>();
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject();
            String id = row.get("id").getAsString();
            if (!pageIds.add(id)) throw changed();
            JsonObject previous = expenses.putIfAbsent(id, row.deepCopy());
            if (previous != null && !previous.equals(row)) throw changed();
        }
        if (!hasMore) {
            // Never reconcile deletions against an incomplete set, even when the
            // server terminates normally after returning overlapping pages.
            if (expenses.size() != cursor + rows.size()) throw changed();
            complete = true;
            return;
        }
        int nextCursor = page.get("nextCursor").getAsInt();
        if (rows.isEmpty() || nextCursor != cursor + rows.size()) throw changed();
        String tailKey = orderKey(rows.get(rows.size() - 1).getAsJsonObject());
        int tailStart = rows.size() - 1;
        while (tailStart > 0 && tailKey.equals(orderKey(rows.get(tailStart - 1).getAsJsonObject()))) {
            tailStart--;
        }
        if (tailStart == 0) {
            // A bucket filling the whole page cannot be enumerated safely with
            // this API's maximum limit. Do not silently import only part of it.
            throw new IllegalStateException("Spliit has too many expenses with identical timestamps "
                    + "to paginate safely. The server needs a stable expense ID sort order.");
        }
        cursor += tailStart;
    }

    public List<String> getExpenseIds() {
        if (!complete) throw new IllegalStateException("Spliit expense list is incomplete");
        return new ArrayList<>(expenses.keySet());
    }

    private static String orderKey(JsonObject row) {
        return Instant.parse(row.get("expenseDate").getAsString()) + "\n"
                + Instant.parse(row.get("createdAt").getAsString());
    }

    private static IllegalStateException changed() {
        return new IllegalStateException("Spliit expenses changed during pagination. Retry sync; "
                + "no incomplete expense list will be applied.");
    }
}
