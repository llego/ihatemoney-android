package net.llego.ihatemoney.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Reads through the prior cursor's entire timestamp bucket to handle tied ordering. */
public final class SpliitActivityWindow {
    private final String marker;
    private final Set<String> seen = new HashSet<>();
    private final Set<String> changed = new HashSet<>();
    private Instant markerTime;
    private Instant previousTime;
    private String newest;
    private int cursor;
    private boolean complete;
    private boolean failed;

    public SpliitActivityWindow(String marker) { this.marker = marker; }
    public int cursor() { return cursor; }
    public boolean complete() { return complete; }
    public boolean usable() { return complete && !failed && markerTime != null; }
    public String newest() { return newest; }
    public Set<String> changed() { return new HashSet<>(changed); }

    public void accept(JsonObject page) {
        if (complete) throw new IllegalStateException("Activity scan already finished");
        try {
            JsonArray rows = page.getAsJsonArray("activities");
            boolean hasMore = page.get("hasMore").getAsBoolean();
            for (JsonElement element : rows) {
                JsonObject row = element.getAsJsonObject();
                String id = row.get("id").getAsString();
                Instant time = Instant.parse(row.get("time").getAsString());
                if (!seen.add(id) || (previousTime != null && time.isAfter(previousTime))) throw new IllegalArgumentException();
                previousTime = time;
                if (newest == null) newest = id;
                if (id.equals(marker)) markerTime = time;
                if (markerTime != null && time.isBefore(markerTime)) {
                    complete = true;
                    return;
                }
                String type = row.get("activityType").getAsString();
                if (type.equals("CREATE_EXPENSE") || type.equals("UPDATE_EXPENSE") || type.equals("DELETE_EXPENSE")) {
                    if (!row.has("expenseId") || row.get("expenseId").isJsonNull()) throw new IllegalArgumentException();
                    changed.add(row.get("expenseId").getAsString());
                } else if (!type.equals("UPDATE_GROUP")) {
                    // Unknown activity types may indicate a new expense mutation API.
                    throw new IllegalArgumentException();
                }
            }
            if (!hasMore) { complete = true; return; }
            int next = page.get("nextCursor").getAsInt();
            if (rows.size() == 0 || next != cursor + rows.size()) throw new IllegalArgumentException();
            cursor = next;
        } catch (RuntimeException e) {
            failed = true;
            complete = true;
        }
    }
}
