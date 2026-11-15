package net.llego.ihatemoney.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
}
