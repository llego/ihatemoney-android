package net.llego.ihatemoney.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;

/** Builds a group update from a fresh server snapshot without changing group settings. */
public final class SpliitGroupForm {
    private SpliitGroupForm() { }

    public static JsonObject fromSnapshot(String snapshot) {
        JsonObject group = SpliitExpenseCodec.parse(snapshot);
        JsonObject form = new JsonObject();
        for (String field : new String[]{"name", "information", "currency", "currencyCode"}) {
            if (!group.has(field)) throw new IllegalArgumentException("Missing group field: " + field);
            form.add(field, group.get(field).deepCopy());
        }
        if (form.get("information").isJsonNull()) form.addProperty("information", "");
        if (!group.has("participants") || !group.get("participants").isJsonArray()) {
            throw new IllegalArgumentException("Missing participants");
        }
        JsonArray participants = new JsonArray();
        for (JsonElement value : group.getAsJsonArray("participants")) {
            JsonObject participant = value.getAsJsonObject();
            if (!participant.has("id") || participant.get("id").isJsonNull()
                    || !participant.has("name") || participant.get("name").isJsonNull()) {
                throw new IllegalArgumentException("Invalid participant");
            }
            JsonObject row = new JsonObject();
            row.add("id", participant.get("id").deepCopy());
            row.add("name", participant.get("name").deepCopy());
            participants.add(row);
        }
        form.add("participants", participants);
        return form;
    }

    /** Add one participant, leaving server-assigned IDs on existing participants intact. */
    public static JsonObject add(String snapshot, String name) {
        JsonObject form = fromSnapshot(snapshot);
        validateName(name);
        JsonArray participants = form.getAsJsonArray("participants");
        if (participants.size() >= 100) throw new IllegalArgumentException("Too many participants");
        ensureUniqueName(participants, name, null);
        JsonObject participant = new JsonObject();
        participant.addProperty("name", name);
        participants.add(participant);
        return form;
    }

    /** Rename exactly one existing participant; never reconstruct the list from local rows. */
    public static JsonObject rename(String snapshot, String id, String name) {
        validateName(name);
        JsonObject form = fromSnapshot(snapshot);
        JsonArray participants = form.getAsJsonArray("participants");
        ensureUniqueName(participants, name, id);
        JsonObject found = null;
        for (JsonElement value : participants) {
            JsonObject participant = value.getAsJsonObject();
            if (participant.get("id").getAsString().equals(id)) {
                if (found != null) throw new IllegalArgumentException("Duplicate participant ID");
                found = participant;
            }
        }
        if (found == null) throw new IllegalArgumentException("Participant is no longer in group");
        found.addProperty("name", name);
        return form;
    }

    private static void validateName(String name) {
        if (name == null || name.length() < 2 || name.length() > 50 || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid participant name");
        }
    }

    private static void ensureUniqueName(JsonArray participants, String name, String exceptId) {
        Set<String> ids = new HashSet<>();
        for (JsonElement value : participants) {
            JsonObject participant = value.getAsJsonObject();
            String id = participant.get("id").getAsString();
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate participant ID");
            if (!id.equals(exceptId) && name.equals(participant.get("name").getAsString())) {
                throw new IllegalArgumentException("Duplicate participant name");
            }
        }
    }
}
