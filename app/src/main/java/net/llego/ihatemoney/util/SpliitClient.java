package net.llego.ihatemoney.util;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import net.llego.ihatemoney.model.SyncProgress;
import net.llego.ihatemoney.model.SyncProgress.Stage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import at.bitfire.cert4android.CustomCertManager;

/**
 * tRPC client for a self-hosted Spliit instance.
 *
 * <p>Spliit is a Next.js app that exposes its tRPC procedures as JSON endpoints:
 * {@code GET /api/trpc/<procedure>?input=<urlencoded JSON>}. The response is
 * wrapped in {@code {"result":{"data":{"json":...}}}}. Groups are anonymous
 * (the group id is the capability), so no credentials are sent.</p>
 */
@WorkerThread
public class SpliitClient {

    private static final String TAG = SpliitClient.class.getSimpleName();

    private static final int PAGE_SIZE = 100;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final String USER_AGENT = "IHateMoney-Android";

    private final String baseUrl;
    private final String groupId;

    public interface ProgressListener {
        void onProgress(Stage stage, int completed, int total);
    }

    public SpliitClient(String baseUrl, String groupId) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.groupId = groupId;
    }

    public static class SpliitGroup {
        public final String id;
        public final String name;
        public final String currency;
        public final String currencyCode;
        public final List<SpliitParticipant> participants;
        public final List<SpliitExpense> expenses;

        SpliitGroup(String id, String name, String currency, String currencyCode,
                    List<SpliitParticipant> participants, List<SpliitExpense> expenses) {
            this.id = id;
            this.name = name;
            this.currency = currency;
            this.currencyCode = currencyCode;
            this.participants = participants;
            this.expenses = expenses;
        }
    }

    public static class SpliitParticipant {
        public final String id;
        public final String name;

        SpliitParticipant(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class SpliitExpense {
        public final String id;
        public final String title;
        /** Amount in cents. */
        public final long amount;
        /** Expense date in seconds since the epoch. */
        public final long expenseDate;
        public final String createdAt;
        public final String paidBy;
        public final List<String> paidFor;
        public final String notes;
        public final String category;
        public final String splitMode;
        public final boolean isReimbursement;
        /** Complete detail response, including metadata absent from the list endpoint. */
        public final String raw;

        SpliitExpense(String id, String title, long amount, long expenseDate,
                      String createdAt, String paidBy, List<String> paidFor,
                      String notes, String category, String splitMode, boolean isReimbursement,
                      String raw) {
            this.id = id;
            this.title = title;
            this.amount = amount;
            this.expenseDate = expenseDate;
            this.createdAt = createdAt;
            this.paidBy = paidBy;
            this.paidFor = paidFor;
            this.notes = notes;
            this.category = category;
            this.splitMode = splitMode;
            this.isReimbursement = isReimbursement;
            this.raw = raw;
        }
    }

    /** Fetch group metadata without downloading its expense history, e.g. when joining. */
    public SpliitGroup getGroupMetadata(@Nullable CustomCertManager ccm) throws IOException, JSONException {
        JSONObject details = getGroupDetails(ccm);
        JSONObject group = details.optJSONObject("group");
        if (group == null) {
            throw new JSONException("Spliit response is missing the group object");
        }

        List<SpliitParticipant> participants = new ArrayList<>();
        JSONArray participantsArray = group.getJSONArray("participants");
        if (participantsArray != null) {
            for (int i = 0; i < participantsArray.length(); i++) {
                JSONObject participant = participantsArray.getJSONObject(i);
                participants.add(new SpliitParticipant(
                        participant.getString("id"), participant.optString("name", "")));
            }
        }

        return new SpliitGroup(
                group.optString("id", groupId),
                group.optString("name", ""),
                group.optString("currency", ""),
                group.optString("currencyCode", ""),
                participants,
                new ArrayList<>());
    }

    /** Fetch a complete, validated ID list before loading any expense details. */
    public SpliitGroup getGroup(@Nullable CustomCertManager ccm) throws IOException, JSONException {
        return getGroup(ccm, (stage, completed, total) -> { });
    }

    public SpliitGroup getGroup(@Nullable CustomCertManager ccm, ProgressListener progress)
            throws IOException, JSONException {
        progress.onProgress(Stage.PROJECT, 0, SyncProgress.UNKNOWN_TOTAL);
        SpliitGroup group = getGroupMetadata(ccm);
        SpliitExpensePagination pagination = new SpliitExpensePagination();
        progress.onProgress(Stage.LISTING, 0, SyncProgress.UNKNOWN_TOTAL);
        try {
            while (!pagination.isComplete()) {
                JSONObject page = getExpensesPage(pagination.getCursor(), ccm);
                pagination.accept(SpliitExpenseCodec.parse(page.toString()));
                progress.onProgress(Stage.LISTING, pagination.getDiscoveredCount(), SyncProgress.UNKNOWN_TOTAL);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new JSONException(e.getMessage());
        }
        List<SpliitExpense> expenses = new ArrayList<>();
        List<String> ids = pagination.getExpenseIds();
        progress.onProgress(Stage.DOWNLOADING, 0, ids.size());
        for (String id : ids) {
            expenses.add(getExpense(id, ccm));
            progress.onProgress(Stage.DOWNLOADING, expenses.size(), ids.size());
        }
        return new SpliitGroup(group.id, group.name, group.currency, group.currencyCode,
                group.participants, expenses);
    }

    private JSONObject getGroupDetails(@Nullable CustomCertManager ccm) throws IOException, JSONException {
        JSONObject input = new JSONObject();
        input.put("json", new JSONObject().put("groupId", groupId));
        return getTrpc("groups.getDetails", input, ccm);
    }

    private JSONObject getExpensesPage(int cursor, @Nullable CustomCertManager ccm)
            throws IOException, JSONException {
        JSONObject input = new JSONObject();
        input.put("json", new JSONObject()
                .put("groupId", groupId)
                .put("cursor", cursor)
                .put("limit", PAGE_SIZE));
        return getTrpc("groups.expenses.list", input, ccm);
    }

    private JSONObject getTrpc(String procedure, JSONObject input, @Nullable CustomCertManager ccm)
            throws IOException, JSONException {
        return requestTrpc(procedure, input, false, ccm);
    }

    public String saveExpense(String expenseId, boolean create, String form,
                              @Nullable CustomCertManager ccm) throws IOException, JSONException {
        JSONObject values = new JSONObject().put("groupId", groupId)
                .put("expenseId", expenseId).put("expenseFormValues", new JSONObject(form));
        JSONObject result = requestTrpc("groups.expenses." + (create ? "create" : "update"),
                new JSONObject().put("json", values), true, ccm);
        return result.getString("expenseId");
    }

    public SpliitExpense getExpense(String expenseId, @Nullable CustomCertManager ccm)
            throws IOException, JSONException {
        JSONObject input = new JSONObject().put("json", new JSONObject()
                .put("groupId", groupId).put("expenseId", expenseId));
        JSONObject raw = getTrpc("groups.expenses.get", input, ccm).getJSONObject("expense");
        if (!expenseId.equals(raw.getString("id")) || !groupId.equals(raw.getString("groupId"))) {
            throw new JSONException("Spliit returned an expense from another group");
        }
        return parseExpense(raw);
    }

    public void deleteExpense(String expenseId, @Nullable CustomCertManager ccm)
            throws IOException, JSONException {
        requestTrpc("groups.expenses.delete", new JSONObject().put("json", new JSONObject()
                .put("groupId", groupId).put("expenseId", expenseId)), true, ccm);
    }

    private JSONObject requestTrpc(String procedure, JSONObject input, boolean mutation,
                                   @Nullable CustomCertManager ccm) throws IOException, JSONException {
        String url = baseUrl + "/api/trpc/" + procedure + (mutation ? ""
                : "?input=" + URLEncoder.encode(input.toString(), "UTF-8"));
        HttpURLConnection connection = null;
        try {
            connection = SupportUtil.getHttpURLConnection(ccm, url);
            connection.setRequestMethod(mutation ? "POST" : "GET");
            // Never forward a capability-bearing mutation through a redirect.
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            if (mutation) {
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(input.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }

            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
            String content = readAll(stream);
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("Spliit request failed (" + responseCode + ")");
            }
            JSONObject root = new JSONObject(content);
            return root.getJSONObject("result").getJSONObject("data").getJSONObject("json");
        } catch (JSONException e) {
            Log.e(TAG, "Could not parse Spliit response for " + procedure, e);
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private SpliitExpense parseExpense(JSONObject expense) throws JSONException {
        String id = expense.getString("id");
        String title = expense.optString("title", "");
        long amount = expense.getLong("amount");
        long expenseDate = parseTimestamp(expense.getString("expenseDate"));
        String createdAt = expense.optString("createdAt", "");

        String paidBy = expense.getJSONObject("paidBy").getString("id");

        List<String> paidFor = new ArrayList<>();
        JSONArray paidForArray = expense.getJSONArray("paidFor");
        if (paidForArray != null) {
            for (int i = 0; i < paidForArray.length(); i++) {
                JSONObject participant = paidForArray.getJSONObject(i).optJSONObject("participant");
                paidFor.add(participant != null ? participant.getString("id")
                        : paidForArray.getJSONObject(i).getString("participantId"));
            }
        }

        String notes = expense.isNull("notes") ? "" : expense.optString("notes", "");
        String category = "";
        JSONObject categoryObject = expense.optJSONObject("category");
        if (categoryObject != null) {
            category = categoryObject.optString("name", "");
        }
        String splitMode = expense.optString("splitMode", "EVENLY");
        boolean isReimbursement = expense.optBoolean("isReimbursement", false);

        return new SpliitExpense(id, title, amount, expenseDate, createdAt,
                paidBy, paidFor, notes, category, splitMode, isReimbursement, expense.toString());
    }

    private static long parseTimestamp(String isoDate) throws JSONException {
        try {
            return Instant.parse(isoDate).getEpochSecond();
        } catch (DateTimeParseException e) {
            throw new JSONException("Invalid Spliit expense date");
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
