package net.llego.ihatemoney.util;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.model.DBCurrency;
import net.llego.ihatemoney.model.DBMember;
import net.llego.ihatemoney.model.DBProject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import at.bitfire.cert4android.CustomCertManager;

@WorkerThread
public class VersatileProjectSyncClient {

    private static final String TAG = VersatileProjectSyncClient.class.getSimpleName();

    /**
     * This entity class is used to return relevant data of the HTTP reponse.
     */
    public static class ResponseData {
        private final String content;
        private final String etag;
        private final long lastModified;

        public ResponseData(String content, String etag, long lastModified) {
            this.content = content;
            this.etag = etag;
            this.lastModified = lastModified;
        }

        public String getContent() {
            return content;
        }

        public String getETag() {
            return etag;
        }

        public long getLastModified() {
            return lastModified;
        }
    }

    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";
    public static final String METHOD_PUT = "PUT";
    public static final String METHOD_DELETE = "DELETE";
    public static final String JSON_ID = "id";
    public static final String JSON_TITLE = "title";
    public static final String JSON_ETAG = "etag";
    private static final String application_json = "application/json";

    private String url;
    private String username;
    private String password;
    private Context context;

    public VersatileProjectSyncClient(String url, String username, String password, Context context) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.context = context;
    }

    public ServerResponse.ProjectResponse getProject(CustomCertManager ccm, DBProject project, long lastModified, String lastETag) throws IOException {
        String target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId();
        String username = project.getRemoteId();
        String password = project.getPassword();
        String bearerToken = project.getBearerToken();
        return new ServerResponse.ProjectResponse(
                requestServer(
                        ccm, target, METHOD_GET, null, null, lastETag,
                        username, password, bearerToken, false
                ), false
        );
    }

    public ServerResponse.EditRemoteProjectResponse editRemoteProject(
            CustomCertManager ccm, DBProject project, @Nullable String newName, @Nullable String newEmail, @Nullable String newPassword,
            @Nullable String newMainCurrencyName) throws IOException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        if (newName != null) {
            paramKeys.add("name");
            paramValues.add(newName);
        }
        if (newEmail != null) {
            paramKeys.add("contact_email");
            paramValues.add(newEmail);
        }
        if (newPassword != null) {
            paramKeys.add("password");
            paramValues.add(newPassword);
        }

        String target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId();
        String username = project.getRemoteId();
        String password = project.getPassword();
        String bearerToken = project.getBearerToken();
        return new ServerResponse.EditRemoteProjectResponse(
                requestServer(
                        ccm, target, METHOD_PUT, paramKeys, paramValues, null,
                        username, password, bearerToken, false
                ), false
        );
    }

    public ServerResponse.EditRemoteMemberResponse editRemoteMember(CustomCertManager ccm, DBProject project, DBMember member) throws IOException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("name");
        paramValues.add(member.getName());
        paramKeys.add("weight");
        paramValues.add(String.valueOf(member.getWeight()));
        paramKeys.add("activated");
        paramValues.add(member.isActivated() ? "true" : "false");

        String target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId() + "/members/" + member.getRemoteId();
        String username = project.getRemoteId();
        String password = project.getPassword();
        String bearerToken = project.getBearerToken();
        return new ServerResponse.EditRemoteMemberResponse(
                requestServer(
                        ccm, target, METHOD_PUT, paramKeys, paramValues, null,
                        username, password, bearerToken, false
                ), false
        );
    }

    public ServerResponse.EditRemoteBillResponse editRemoteBill(CustomCertManager ccm, DBProject project, DBBill bill, Map<Long, Long> memberIdToRemoteId) throws IOException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        // we keep sending date for IHateMoney
        paramKeys.add("date");
        paramValues.add(bill.getDate());
        paramKeys.add("what");
        paramValues.add(bill.getWhat());
        paramKeys.add("payer");
        paramValues.add(
                String.valueOf(
                        memberIdToRemoteId.get(bill.getPayerId())
                )
        );
        paramKeys.add("amount");
        paramValues.add(SupportUtil.dotNumberFormat.format(bill.getAmount()));

        String target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId() + "/bills/" + bill.getRemoteId();
        String username = project.getRemoteId();
        String password = project.getPassword();
        String bearerToken = project.getBearerToken();
        for (long boId : bill.getBillOwersIds()) {
            paramKeys.add("payed_for");
            paramValues.add(
                    String.valueOf(
                            memberIdToRemoteId.get(boId)
                    )
            );
        }
        return new ServerResponse.EditRemoteBillResponse(
                requestServer(
                        ccm, target, METHOD_PUT, paramKeys, paramValues, null,
                        username, password, bearerToken, false
                ), false
        );
    }

    public ServerResponse.DeleteRemoteProjectResponse deleteRemoteProject(CustomCertManager ccm, DBProject project) throws IOException {
        String target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId();
        String username = project.getRemoteId();
        String password = project.getPassword();
        String bearerToken = project.getBearerToken();
        return new ServerResponse.DeleteRemoteProjectResponse(
                requestServer(
                        ccm, target, METHOD_DELETE, null, null,
                        null, username, password, bearerToken, false
                ), false
        );
    }

    public ServerResponse.DeleteRemoteBillResponse deleteRemoteBill(CustomCertManager ccm, DBProject project, long billRemoteId) throws IOException {
        String target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId() + "/bills/" + billRemoteId;
        String username = project.getRemoteId();
        String password = project.getPassword();
        String bearerToken = project.getBearerToken();
        return new ServerResponse.DeleteRemoteBillResponse(
                requestServer(
                        ccm, target, METHOD_DELETE, null, null,
                        null, username, password, bearerToken, false
                ), false
        );
    }

    public ServerResponse.CreateRemoteProjectResponse createAnonymousRemoteProject(CustomCertManager ccm, DBProject project) throws IOException {
        String target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects";
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("name");
        paramValues.add(project.getName() == null ? "" : project.getName());
        paramKeys.add("contact_email");
        paramValues.add(project.getEmail() == null ? "" : project.getEmail());
        paramKeys.add("password");
        paramValues.add(project.getPassword() == null ? "" : project.getPassword());
        paramKeys.add("id");
        paramValues.add(project.getRemoteId() == null ? "" : project.getRemoteId());
        return new ServerResponse.CreateRemoteProjectResponse(
                requestServer(
                        ccm, target, METHOD_POST, paramKeys, paramValues,
                        null, null, null, null, false
                ), false
        );
    }

    public ServerResponse.CreateRemoteBillResponse createRemoteBill(CustomCertManager ccm, DBProject project, DBBill bill, Map<Long, Long> memberIdToRemoteId) throws IOException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        // we keep sending date for IHateMoney
        paramKeys.add("date");
        paramValues.add(bill.getDate());
        paramKeys.add("what");
        paramValues.add(bill.getWhat());
        paramKeys.add("payer");
        paramValues.add(
                String.valueOf(
                        memberIdToRemoteId.get(bill.getPayerId())
                )
        );
        paramKeys.add("amount");
        paramValues.add(SupportUtil.dotNumberFormat.format(bill.getAmount()));

        String target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId() + "/bills";
        String username = project.getRemoteId();
        String password = project.getPassword();
        String bearerToken = project.getBearerToken();

        for (long boId : bill.getBillOwersIds()) {
            paramKeys.add("payed_for");
            paramValues.add(
                    String.valueOf(
                            memberIdToRemoteId.get(boId)
                    )
            );
        }

        return new ServerResponse.CreateRemoteBillResponse(
                requestServer(
                        ccm, target, METHOD_POST, paramKeys, paramValues, null,
                        username, password, bearerToken, false
                ), false
        );
    }

    public ServerResponse.CreateRemoteMemberResponse createRemoteMember(CustomCertManager ccm, DBProject project, DBMember member) throws IOException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("name");
        paramValues.add(member.getName());

        String target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId() + "/members";
        String username = project.getRemoteId();
        String password = project.getPassword();
        String bearerToken = project.getBearerToken();

        return new ServerResponse.CreateRemoteMemberResponse(
                requestServer(
                        ccm, target, METHOD_POST, paramKeys, paramValues, null,
                        username, password, bearerToken, false
                ), false, false
        );
    }

    public ServerResponse.BillsResponse getBills(CustomCertManager ccm, DBProject project) throws IOException {
        String target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId() + "/bills";
        String username = project.getRemoteId();
        String password = project.getPassword();
        String bearerToken = project.getBearerToken();
        return new ServerResponse.BillsResponse(
                requestServer(
                        ccm, target, METHOD_GET, null, null,
                        null, username, password, bearerToken, false
                ), false
        );
    }

    public ServerResponse.MembersResponse getMembers(CustomCertManager ccm, DBProject project) throws IOException {
        String target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId() + "/members";
        String username = project.getRemoteId();
        String password = project.getPassword();
        String bearerToken = project.getBearerToken();
        return new ServerResponse.MembersResponse(
                requestServer(
                        ccm, target, METHOD_GET, null, null,
                        null, username, password, bearerToken, false
                ), false
        );
    }

    public ServerResponse.CreateRemoteCurrencyResponse createRemoteCurrency(CustomCertManager ccm, DBProject project, DBCurrency currency) throws IOException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("name");
        paramValues.add(currency.getName());
        paramKeys.add("rate");
        paramValues.add(String.valueOf(currency.getExchangeRate()));

        String target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId() + "/currency";
        String username = project.getRemoteId();
        String password = project.getPassword();
        String bearerToken = project.getBearerToken();

        return new ServerResponse.CreateRemoteCurrencyResponse(
                requestServer(
                        ccm, target, METHOD_POST, paramKeys, paramValues, null,
                        username, password, bearerToken, false
                ), false
        );
    }

    public ServerResponse.EditRemoteCurrencyResponse editRemoteCurrency(CustomCertManager ccm, DBProject project, DBCurrency currency) throws IOException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("name");
        paramValues.add(currency.getName());
        paramKeys.add("rate");
        paramValues.add(String.valueOf(currency.getExchangeRate()));

        String target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId() + "/currency/" + currency.getRemoteId();
        String username = project.getRemoteId();
        String password = project.getPassword();
        String bearerToken = project.getBearerToken();
        return new ServerResponse.EditRemoteCurrencyResponse(
                requestServer(
                        ccm, target, METHOD_PUT, paramKeys, paramValues, null,
                        username, password, bearerToken, false
                ), false
        );
    }

    public ServerResponse.DeleteRemoteCurrencyResponse deleteRemoteCurrency(CustomCertManager ccm, DBProject project, long currencyRemoteId) throws IOException {
        String target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId() + "/currency/" + currencyRemoteId;
        String username = project.getRemoteId();
        String password = project.getPassword();
        String bearerToken = project.getBearerToken();
        return new ServerResponse.DeleteRemoteCurrencyResponse(
                requestServer(
                        ccm, target, METHOD_DELETE, null, null,
                        null, username, password, bearerToken, false
                ), false
        );
    }

    /**
     * Request-Method for POST, PUT with or without JSON-Object-Parameter
     *
     * @param target Filepath to the wanted function
     * @param method GET, POST, DELETE or PUT
     * @return Body of answer
     * @throws IOException
     */
    private ResponseData requestServer(CustomCertManager ccm, String target, String method,
                                       List<String> paramKeys, List<String> paramValues,
                                       String lastETag, String username, String password,
                                       @Nullable String bearerToken, boolean isOCSRequest)
            throws IOException {
        StringBuffer result = new StringBuffer();
        // setup connection
        String targetURL = target;
        HttpURLConnection con = SupportUtil.getHttpURLConnection(ccm, targetURL);
        con.setRequestMethod(method);
        if (bearerToken != null) {
            con.setRequestProperty("Authorization", "Bearer " + bearerToken);
        } else if (username != null) {
            con.setRequestProperty("Authorization", "Basic " + Base64.encodeToString((username + ":" + password).getBytes(), Base64.NO_WRAP));
        }
        con.setRequestProperty("User-Agent", "MoneyBuster/" + SupportUtil.getAppVersionName(context));
        if (lastETag != null && METHOD_GET.equals(method)) {
            con.setRequestProperty("If-None-Match", lastETag);
        }
        if (isOCSRequest) {
            con.setRequestProperty("OCS-APIRequest", "true");
            con.setRequestProperty("Accept", "application/json");
        }
        con.setConnectTimeout(10 * 1000); // 10 seconds
        Log.d(getClass().getSimpleName(), method + " " + targetURL);
        // send request data (optional)
        if (paramKeys != null) {
            String dataString = "";
            for (int i = 0; i < paramKeys.size(); i++) {
                String key = paramKeys.get(i);
                String value = paramValues.get(i);
                if (dataString.length() > 0) {
                    dataString += "&";
                }
                dataString += URLEncoder.encode(key, "UTF-8") + "=";
                dataString += URLEncoder.encode(value, "UTF-8");
            }
            byte[] data = dataString.getBytes();

            Log.d(getClass().getSimpleName(), "Params: " + dataString);
            con.setFixedLengthStreamingMode(data.length);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            con.setRequestProperty("Content-Length", Integer.toString(data.length));
            con.setDoOutput(true);
            OutputStream os = con.getOutputStream();
            os.write(data);
            os.flush();
            os.close();
        }
        // read response data
        int responseCode = con.getResponseCode();
        Log.d(getClass().getSimpleName(), "HTTP response code: " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            throw new ServerResponse.NotModifiedException();
        }

        Log.d(TAG, "METHOD : " + method);
        BufferedReader rd;
        if (responseCode >= 200 && responseCode < 400) {
            rd = new BufferedReader(new InputStreamReader(con.getInputStream()));
        } else {
            Log.e(TAG, "ERROR CODE : " + responseCode);
            rd = new BufferedReader(new InputStreamReader(con.getErrorStream()));
        }
        try (BufferedReader reader = rd) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }
        if (responseCode >= 400) {
            throw new IOException(result.toString());
        }
        // create response object
        String etag = con.getHeaderField("ETag");
        long lastModified = con.getHeaderFieldDate("Last-Modified", 0) / 1000;
        Log.i(TAG, "Result length:  " + result.length());
        Log.d(TAG, "ETag: " + etag + "; Last-Modified: " + lastModified + " (" + con.getHeaderField("Last-Modified") + ")");
        // return these header fields since they should only be saved after successful processing the result!
        return new ResponseData(result.toString(), etag, lastModified);
    }
}