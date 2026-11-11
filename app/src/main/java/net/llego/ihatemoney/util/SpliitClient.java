package net.llego.ihatemoney.util;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import at.bitfire.cert4android.CustomCertManager;

/**
 * Read-only client for a self-hosted Spliit instance.
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
    private static final String SPLIIT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final String USER_AGENT = "IHateMoney-Android";

    private final String baseUrl;
    private final String groupId;

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

        SpliitExpense(String id, String title, long amount, long expenseDate,
                      String createdAt, String paidBy, List<String> paidFor,
                      String notes, String category, String splitMode, boolean isReimbursement) {
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
        }
    }

    /**
     * Fetch the group and all of its expenses. The group is a single tRPC call;
     * expenses are paginated and fetched page by page.
     */
    public SpliitGroup getGroup(@Nullable CustomCertManager ccm) throws IOException, JSONException {
        JSONObject group = getGroupDetails(ccm);

        List<SpliitParticipant> participants = new ArrayList<>();
        JSONArray participantsArray = group.optJSONArray("participants");
        if (participantsArray != null) {
            for (int i = 0; i < participantsArray.length(); i++) {
                JSONObject participant = participantsArray.getJSONObject(i);
                participants.add(new SpliitParticipant(
                        participant.getString("id"), participant.optString("name", "")));
            }
        }

        List<SpliitExpense> expenses = new ArrayList<>();
        int cursor = 0;
        while (true) {
            JSONObject page = getExpensesPage(cursor, ccm);
            JSONArray expensesArray = page.optJSONArray("expenses");
            if (expensesArray != null) {
                for (int i = 0; i < expensesArray.length(); i++) {
                    expenses.add(parseExpense(expensesArray.getJSONObject(i)));
                }
            }
            if (!page.optBoolean("hasMore", false)) {
                break;
            }
            int nextCursor = page.optInt("nextCursor", cursor + PAGE_SIZE);
            if (nextCursor <= cursor) {
                // Defensive: never loop forever if the server reports hasMore without advancing.
                break;
            }
            cursor = nextCursor;
        }

        return new SpliitGroup(
                group.optString("id", groupId),
                group.optString("name", ""),
                group.optString("currency", ""),
                group.optString("currencyCode", ""),
                participants,
                expenses);
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
        String url = baseUrl + "/api/trpc/" + procedure
                + "?input=" + URLEncoder.encode(input.toString(), "UTF-8");
        HttpURLConnection connection = null;
        try {
            connection = SupportUtil.getHttpURLConnection(ccm, url);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
            String content = readAll(stream);
            if (responseCode >= 400) {
                throw new IOException("Spliit request failed (" + responseCode + "): " + content);
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
        long amount = expense.optLong("amount", 0);
        long expenseDate = parseTimestamp(expense.optString("expenseDate", ""));
        String createdAt = expense.optString("createdAt", "");

        String paidBy = "";
        JSONObject paidByObject = expense.optJSONObject("paidBy");
        if (paidByObject != null) {
            paidBy = paidByObject.optString("id", "");
        }

        List<String> paidFor = new ArrayList<>();
        JSONArray paidForArray = expense.optJSONArray("paidFor");
        if (paidForArray != null) {
            for (int i = 0; i < paidForArray.length(); i++) {
                JSONObject participant = paidForArray.getJSONObject(i).optJSONObject("participant");
                if (participant != null) {
                    paidFor.add(participant.optString("id", ""));
                }
            }
        }

        String notes = expense.optString("notes", "");
        String category = "";
        JSONObject categoryObject = expense.optJSONObject("category");
        if (categoryObject != null) {
            category = categoryObject.optString("name", "");
        }
        String splitMode = expense.optString("splitMode", "EVENLY");
        boolean isReimbursement = expense.optBoolean("isReimbursement", false);

        return new SpliitExpense(id, title, amount, expenseDate, createdAt,
                paidBy, paidFor, notes, category, splitMode, isReimbursement);
    }

    private static long parseTimestamp(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) {
            return 0;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(SPLIIT_DATE_FORMAT, Locale.ROOT);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Date date = sdf.parse(isoDate);
            return date == null ? 0 : date.getTime() / 1000;
        } catch (ParseException e) {
            return 0;
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
