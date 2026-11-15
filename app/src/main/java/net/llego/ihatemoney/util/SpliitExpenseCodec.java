package net.llego.ihatemoney.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.model.DBBillOwer;
import net.llego.ihatemoney.model.DBMember;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Lossless wire metadata lives alongside, not inside, the shared expense model. */
public final class SpliitExpenseCodec {
    private SpliitExpenseCodec() { }

    public static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    public static boolean same(String first, String second) {
        return first == null ? second == null
                : second != null && JsonParser.parseString(first).equals(JsonParser.parseString(second));
    }

    public static String text(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    public static boolean editable(String baseline) {
        if (baseline == null) return false;
        JsonObject raw = parse(baseline);
        return "EVENLY".equals(text(raw, "splitMode", ""))
                && "NONE".equals(text(raw, "recurrenceRule", "NONE"))
                && text(raw, "originalCurrency", "").isEmpty()
                && raw.has("documents") && raw.has("notes");
    }

    /** Project a full expense response into the form accepted by Spliit's mutations. */
    public static JsonObject remoteForm(String baseline) {
        JsonObject raw = parse(baseline);
        JsonObject form = new JsonObject();
        for (String field : new String[]{"title", "amount", "expenseDate", "splitMode",
                "isReimbursement", "documents", "recurrenceRule", "originalAmount",
                "originalCurrency", "conversionRate"}) {
            if (raw.has(field) && !raw.get(field).isJsonNull()) {
                form.add(field, raw.get(field).deepCopy());
            }
        }
        form.addProperty("expenseDate", Instant.parse(raw.get("expenseDate").getAsString()).toString());
        form.addProperty("recurrenceRule", text(raw, "recurrenceRule", "NONE"));
        form.addProperty("notes", text(raw, "notes", ""));
        form.addProperty("category", raw.has("categoryId") ? raw.get("categoryId").getAsInt() : 0);
        form.addProperty("paidBy", text(raw, "paidById", ""));
        form.addProperty("saveDefaultSplittingOptions", false);
        JsonArray paidFor = new JsonArray();
        for (JsonElement value : raw.getAsJsonArray("paidFor")) {
            JsonObject row = value.getAsJsonObject();
            JsonObject participant = new JsonObject();
            participant.addProperty("participant", row.get("participantId").getAsString());
            participant.add("shares", "EVENLY".equals(text(raw, "splitMode", ""))
                    ? new com.google.gson.JsonPrimitive(100) : row.get("shares").deepCopy());
            paidFor.add(participant);
        }
        form.add("paidFor", sortedParticipants(paidFor));
        return form;
    }

    public static JsonObject localForm(DBBill bill, String baseline, Map<Long, DBMember> members) {
        if (baseline != null && !editable(baseline)) {
            throw new IllegalArgumentException("Unsupported Spliit expense");
        }
        JsonObject form = baseline == null ? new JsonObject() : remoteForm(baseline);
        String title = bill.getWhat();
        long amount = BigDecimal.valueOf(bill.getAmount()).movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        if (title == null || title.length() < 2 || title.length() > 200
                || amount == 0 || Math.abs(amount) > 1_000_000_000L
                || (bill.getComment() != null && bill.getComment().length() > 5000)
                || !DBBill.NON_REPEATED.equals(bill.getRepeat())) {
            throw new IllegalArgumentException("Invalid Spliit expense");
        }
        form.addProperty("title", title);
        form.addProperty("amount", amount);
        if (baseline == null || Instant.parse(form.get("expenseDate").getAsString()).getEpochSecond()
                != bill.getTimestamp()) {
            form.addProperty("expenseDate", Instant.ofEpochSecond(bill.getTimestamp()).toString());
        }
        form.addProperty("notes", bill.getComment() == null ? "" : bill.getComment());
        form.addProperty("paidBy", remoteMember(members, bill.getPayerId()));
        JsonArray paidFor = new JsonArray();
        for (DBBillOwer ower : bill.getBillOwers()) {
            JsonObject row = new JsonObject();
            row.addProperty("participant", remoteMember(members, ower.getMemberId()));
            row.addProperty("shares", 100);
            paidFor.add(row);
        }
        if (paidFor.size() == 0 || paidFor.size() > 100) {
            throw new IllegalArgumentException("Invalid Spliit participants");
        }
        form.add("paidFor", sortedParticipants(paidFor));
        form.addProperty("splitMode", "EVENLY");
        form.addProperty("saveDefaultSplittingOptions", false);
        if (baseline == null) {
            form.addProperty("category", 0);
            form.addProperty("isReimbursement", false);
            form.addProperty("recurrenceRule", "NONE");
            form.add("documents", new JsonArray());
        }
        return form;
    }

    private static String remoteMember(Map<Long, DBMember> members, long id) {
        DBMember member = members.get(id);
        if (member == null || member.getRemoteIdStr() == null) {
            throw new IllegalArgumentException("Missing Spliit participant");
        }
        return member.getRemoteIdStr();
    }

    private static JsonArray sortedParticipants(JsonArray participants) {
        List<JsonElement> sorted = new ArrayList<>();
        for (JsonElement participant : participants) sorted.add(participant);
        sorted.sort(Comparator.comparing(value -> value.getAsJsonObject().get("participant").getAsString()));
        JsonArray result = new JsonArray();
        for (JsonElement participant : sorted) result.add(participant);
        return result;
    }
}
