package net.eneiluj.moneybuster.util;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.nextcloud.android.sso.QueryParam;
import com.nextcloud.android.sso.aidl.NextcloudRequest;
import com.nextcloud.android.sso.api.NextcloudAPI;
import com.nextcloud.android.sso.api.Response;
import com.nextcloud.android.sso.exceptions.NextcloudHttpRequestFailedException;
import com.nextcloud.android.sso.exceptions.TokenMismatchException;
import com.nextcloud.android.sso.model.SingleSignOnAccount;

import net.eneiluj.moneybuster.model.DBBill;
import net.eneiluj.moneybuster.model.DBCurrency;
import net.eneiluj.moneybuster.model.DBMember;
import net.eneiluj.moneybuster.model.DBProject;
import net.eneiluj.moneybuster.model.ProjectType;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
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
    private NextcloudAPI nextcloudAPI;
    private SingleSignOnAccount ssoAccount;
    @Nullable
    private boolean cospendVersionGT161;
    private Context context;

    public VersatileProjectSyncClient(String url, String username, String password,
                                      @Nullable NextcloudAPI nextcloudAPI, @Nullable SingleSignOnAccount ssoAccount,
                                      @Nullable String cospendVersion, Context context) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.nextcloudAPI = nextcloudAPI;
        this.ssoAccount = ssoAccount;
        if (cospendVersion == null) {
            this.cospendVersionGT161 = false;
            Log.i(TAG, "GT161 is FALSE");
        } else {
            this.cospendVersionGT161 = SupportUtil.compareVersions(cospendVersion, "1.6.1") >= 0;
            Log.i(TAG, "GT161: " + this.cospendVersionGT161);
        }
        this.context = context;
    }

    public boolean canAccessProjectWithNCLogin(DBProject project) {
        return (project.getPassword().equals("")
                && !url.replaceAll("/+$", "").equals("")
                && project.getServerUrl()
                    .replace("/index.php/apps/cospend", "")
                    .equals(url.replaceAll("/+$", ""))
        );
    }

    public boolean canAccessProjectWithSSO(DBProject project) {
        return (project.getPassword().equals("")
                && ssoAccount != null
                && project.getServerUrl().replace("/index.php/apps/cospend", "").equals(ssoAccount.url)
        );
    }

    /**
     * This is normally done by the network lib but apparently not in Android API < 24
     * Can be removed if some day the min API level become higher than 24 (and some tests are made)
     * @param password
     * @return
     * @throws UnsupportedEncodingException
     */
    private String getEncodedPassword(String password) throws UnsupportedEncodingException {
        return URLEncoder.encode(password, "utf-8").replaceAll("\\+", "%20");
    }

    public ServerResponse.ProjectResponse getProject(CustomCertManager ccm, DBProject project, long lastModified, String lastETag) throws JSONException, IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
        String target;
        String username = null;
        String password = null;
        String bearerToken = null;
        boolean useOcsApiRequest = false;
        if (ProjectType.COSPEND.equals(project.getType())) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = this.cospendVersionGT161
                    ? project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.getRemoteId()
                    : project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.getRemoteId();
                useOcsApiRequest = this.cospendVersionGT161;
                if (this.cospendVersionGT161) {
                    Log.i(TAG, "using new API (weblogin, " + username + ":" + password + ") for getProjectInfo");
                }
            } else if (canAccessProjectWithSSO(project)) {
                if (this.cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.getRemoteId();
                    Log.i(TAG, "using new API for getProjectInfo");
                    return new ServerResponse.ProjectResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, null, null, true), true);
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId();
                    return new ServerResponse.ProjectResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, null, null, false), false);
                }
            } else {
                useOcsApiRequest = this.cospendVersionGT161;
                target = this.cospendVersionGT161
                    ? project.getRequestBaseUrl(true)
                        + "/api/v1/public/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword())
                    : project.getRequestBaseUrl(false)
                        + "/api/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword());

                Log.i(TAG, "using public API, target is: "+target+"for getProjectInfo");
            }
        } else {
            target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId();
            username = project.getRemoteId();
            password = project.getPassword();
            bearerToken = project.getBearerToken();
        }

        return new ServerResponse.ProjectResponse(
                requestServer(
                        ccm, target, METHOD_GET, null, null, lastETag,
                        username, password, bearerToken, useOcsApiRequest
                ), useOcsApiRequest
        );
    }

    public ServerResponse.EditRemoteProjectResponse editRemoteProject(
            CustomCertManager ccm, DBProject project, @Nullable String newName, @Nullable String newEmail, @Nullable String newPassword,
            @Nullable String newMainCurrencyName) throws IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
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

        String target;
        String username = null;
        String password = null;
        String bearerToken = null;
        boolean useOcsApiRequest = false;
        if (ProjectType.COSPEND.equals(project.getType())) {
            if (newMainCurrencyName != null) {
                paramKeys.add("currencyname");
                paramValues.add(newMainCurrencyName);
            }
            if (this.cospendVersionGT161) {
                paramKeys.clear();
                paramValues.clear();
                if (newName != null) {
                    paramKeys.add("name");
                    paramValues.add(newName);
                }
                if (newPassword != null) {
                    paramKeys.add("password");
                    paramValues.add(newPassword);
                }
                if (newMainCurrencyName != null) {
                    paramKeys.add("currencyName");
                    paramValues.add(newMainCurrencyName);
                }
            }
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = this.cospendVersionGT161
                        ? project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.getRemoteId()
                        : project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.getRemoteId();
                useOcsApiRequest = this.cospendVersionGT161;
                if (this.cospendVersionGT161) {
                    Log.i(TAG, "using new API (weblogin, " + username + ":" + password + ") for editRemoteProject");
                }
            } else if (canAccessProjectWithSSO(project)) {
                if (this.cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.getRemoteId();
                    Log.i(TAG, "using new API for editRemoteProject");
                    return new ServerResponse.EditRemoteProjectResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_PUT, paramKeys, paramValues, true), true);
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId();
                    return new ServerResponse.EditRemoteProjectResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_PUT, paramKeys, paramValues, false), false);
                }
            } else {
                useOcsApiRequest = this.cospendVersionGT161;
                target = this.cospendVersionGT161
                        ? project.getRequestBaseUrl(true) + "/api/v1/public/projects/"
                            + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword())
                        : project.getRequestBaseUrl(false) + "/api/projects/"
                            + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword());

                Log.i(TAG, "using public API, target is: "+target+"for editRemoteProject");
            }
        } else {
            target = project.getServerUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId();
            //https://ihatemoney.org/api/projects/demo
            username = project.getRemoteId();
            password = project.getPassword();
            bearerToken = project.getBearerToken();
        }
        return new ServerResponse.EditRemoteProjectResponse(
                requestServer(
                        ccm, target, METHOD_PUT, paramKeys, paramValues, null,
                        username, password, bearerToken, useOcsApiRequest
                ), useOcsApiRequest
        );
    }

    public ServerResponse.EditRemoteMemberResponse editRemoteMember(CustomCertManager ccm, DBProject project, DBMember member) throws IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("name");
        paramValues.add(member.getName());
        paramKeys.add("weight");
        paramValues.add(String.valueOf(member.getWeight()));
        paramKeys.add("activated");
        paramValues.add(member.isActivated() ? "true" : "false");

        String target;
        String username = null;
        String password = null;
        String bearerToken = null;
        boolean useOcsApiRequest = false;
        if (ProjectType.COSPEND.equals(project.getType())) {
            // put color if set
            Integer r = member.getR();
            Integer g = member.getG();
            Integer b = member.getB();
            if (r != null && g != null && b != null) {
                String hexColor = "#" + Integer.toHexString(r) + Integer.toHexString(g) + Integer.toHexString(b);
                paramKeys.add("color");
                paramValues.add(hexColor);
            }
            // launch the request
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = this.cospendVersionGT161
                        ? project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.getRemoteId() + "/members/" + member.getRemoteId()
                        : project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.getRemoteId() + "/members/" + member.getRemoteId();
                useOcsApiRequest = this.cospendVersionGT161;
                if (this.cospendVersionGT161) {
                    Log.i(TAG, "using new API (weblogin, " + username + ":" + password + ") for editRemoteMember");
                }
            } else if (canAccessProjectWithSSO(project)) {
                if (this.cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.getRemoteId() + "/members/" + member.getRemoteId();
                    Log.i(TAG, "using new API for editRemoteMember");
                    return new ServerResponse.EditRemoteMemberResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_PUT, paramKeys, paramValues, true), true);
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/members/" + member.getRemoteId();
                    return new ServerResponse.EditRemoteMemberResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_PUT, paramKeys, paramValues, false), false);
                }
            } else {
                useOcsApiRequest = this.cospendVersionGT161;
                target = this.cospendVersionGT161
                    ? project.getRequestBaseUrl(true) + "/api/v1/public/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/members/" + member.getRemoteId()
                    : project.getRequestBaseUrl(false) + "/api/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/members/" + member.getRemoteId();

                Log.i(TAG, "using public API, target is: "+target+"for editRemoteMember");
            }
        } else {
            target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId() + "/members/" + member.getRemoteId();
            username = project.getRemoteId();
            password = project.getPassword();
            bearerToken = project.getBearerToken();
        }

        return new ServerResponse.EditRemoteMemberResponse(
                requestServer(
                        ccm, target, METHOD_PUT, paramKeys, paramValues, null,
                        username, password, bearerToken, useOcsApiRequest
                ), useOcsApiRequest
        );
    }

    public ServerResponse.EditRemoteBillResponse editRemoteBill(CustomCertManager ccm, DBProject project, DBBill bill, Map<Long, Long> memberIdToRemoteId) throws IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        // we keep sending date for IHateMoney and old Cospend versions
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

        String target;
        String username = null;
        String password = null;
        String bearerToken = null;
        boolean useOcsApiRequest = false;
        if (ProjectType.COSPEND.equals(project.getType())) {
            paramKeys.add("timestamp");
            paramValues.add(String.valueOf(bill.getTimestamp()));
            paramKeys.add("comment");
            paramValues.add(bill.getComment());
            paramKeys.add("repeat");
            paramValues.add(bill.getRepeat());

            if (this.cospendVersionGT161) {
                paramKeys.add("payedFor");
                paramKeys.add("paymentMode");
                paramKeys.add("categoryId");
                paramKeys.add("paymentModeId");
            } else {
                paramKeys.add("payed_for");
                paramKeys.add("paymentmode");
                paramKeys.add("categoryid");
                paramKeys.add("paymentmodeid");
            }
            String payedFor = "";
            for (long boId : bill.getBillOwersIds()) {
                payedFor += String.valueOf(memberIdToRemoteId.get(boId)) + ",";
            }
            payedFor = payedFor.replaceAll(",$", "");
            paramValues.add(payedFor);
            paramValues.add(bill.getPaymentMode());
            paramValues.add(String.valueOf(bill.getCategoryRemoteId()));
            paramValues.add(String.valueOf(bill.getPaymentModeRemoteId()));

            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = this.cospendVersionGT161
                        ? project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.getRemoteId() + "/bills/" + bill.getRemoteId()
                        : project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.getRemoteId() + "/bills/" + bill.getRemoteId();
                useOcsApiRequest = this.cospendVersionGT161;
                if (this.cospendVersionGT161) {
                    Log.i(TAG, "using new API (weblogin, " + username + ":" + password + ") for editRemoteBill");
                }
            } else if (canAccessProjectWithSSO(project)) {
                if (this.cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.getRemoteId() + "/bills/" + bill.getRemoteId();
                    Log.i(TAG, "using new API for editRemoteBill");
                    return new ServerResponse.EditRemoteBillResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_PUT, paramKeys, paramValues, true), true);
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/bills/" + bill.getRemoteId();
                    return new ServerResponse.EditRemoteBillResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_PUT, paramKeys, paramValues, false), false);
                }
            } else {
                useOcsApiRequest = this.cospendVersionGT161;
                target = this.cospendVersionGT161
                    ? project.getRequestBaseUrl(true) + "/api/v1/public/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/bills/" + bill.getRemoteId()
                    : project.getRequestBaseUrl(false) + "/api/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/bills/" + bill.getRemoteId();

                Log.i(TAG, "using public API, target is: "+target+"for editRemoteBill");
            }
        } else {
            target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId() + "/bills/" + bill.getRemoteId();
            username = project.getRemoteId();
            password = project.getPassword();
            bearerToken = project.getBearerToken();

            for (long boId : bill.getBillOwersIds()) {
                paramKeys.add("payed_for");
                paramValues.add(
                        String.valueOf(
                                memberIdToRemoteId.get(boId)
                        )
                );
            }
        }
        return new ServerResponse.EditRemoteBillResponse(
                requestServer(
                        ccm, target, METHOD_PUT, paramKeys, paramValues, null,
                        username, password, bearerToken, useOcsApiRequest
                ), useOcsApiRequest
        );
    }

    public ServerResponse.DeleteRemoteProjectResponse deleteRemoteProject(CustomCertManager ccm, DBProject project) throws IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
        String target;
        String username = null;
        String password = null;
        String bearerToken = null;
        boolean useOcsApiRequest = false;
        if (ProjectType.COSPEND.equals(project.getType())) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = this.cospendVersionGT161
                        ? project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.getRemoteId()
                        : project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.getRemoteId();
                useOcsApiRequest = this.cospendVersionGT161;
                if (this.cospendVersionGT161) {
                    Log.i(TAG, "using new API (weblogin, " + username + ":" + password + ") for deleteRemoteProject");
                }
            } else if (canAccessProjectWithSSO(project)) {
                if (this.cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.getRemoteId();
                    Log.i(TAG, "using new API for deleteRemoteProject");
                    return new ServerResponse.DeleteRemoteProjectResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_DELETE, null, null, true), true);
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId();
                    return new ServerResponse.DeleteRemoteProjectResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_DELETE, null, null, false), false);
                }
            } else {
                useOcsApiRequest = this.cospendVersionGT161;
                target = this.cospendVersionGT161
                    ? project.getRequestBaseUrl(true) + "/api/v1/public/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword())
                    : project.getRequestBaseUrl(false) + "/api/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword());

                Log.i(TAG, "using public API, target is: "+target+"for deleteRemoteProject");
            }
        } else {
            target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId();
            username = project.getRemoteId();
            password = project.getPassword();
            bearerToken = project.getBearerToken();
        }
        return new ServerResponse.DeleteRemoteProjectResponse(
                requestServer(
                        ccm, target, METHOD_DELETE, null, null,
                        null, username, password, bearerToken, useOcsApiRequest
                ), useOcsApiRequest
        );
    }

    public ServerResponse.DeleteRemoteBillResponse deleteRemoteBill(CustomCertManager ccm, DBProject project, long billRemoteId) throws IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
        String target;
        String username = null;
        String password = null;
        String bearerToken = null;
        boolean useOcsApiRequest = false;
        if (ProjectType.COSPEND.equals(project.getType())) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = this.cospendVersionGT161
                        ? project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.getRemoteId() + "/bills/" + billRemoteId
                        : project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.getRemoteId() + "/bills/" + billRemoteId;
                useOcsApiRequest = this.cospendVersionGT161;
                if (this.cospendVersionGT161) {
                    Log.i(TAG, "using new API (weblogin, " + username + ":" + password + ") for deleteRemoteBill");
                }
            } else if (canAccessProjectWithSSO(project)) {
                if (this.cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.getRemoteId() + "/bills/" + billRemoteId;
                    Log.i(TAG, "using new API for deleteRemoteProject");
                    return new ServerResponse.DeleteRemoteBillResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_DELETE, null, null, true), true);
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/bills/" + billRemoteId;
                    return new ServerResponse.DeleteRemoteBillResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_DELETE, null, null, false), false);
                }
            } else {
                useOcsApiRequest = this.cospendVersionGT161;
                target = this.cospendVersionGT161
                    ? project.getRequestBaseUrl(true) + "/api/v1/public/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/bills/" + billRemoteId
                    : project.getRequestBaseUrl(false) + "/api/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/bills/" + billRemoteId;

                Log.i(TAG, "using public API, target is: "+target+"for deleteRemoteProject");
            }
        } else {
            target = project.getServerUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/bills/" + billRemoteId;
            username = project.getRemoteId();
            password = project.getPassword();
            bearerToken = project.getBearerToken();
        }
        return new ServerResponse.DeleteRemoteBillResponse(
                requestServer(
                        ccm, target, METHOD_DELETE, null, null,
                        null, username, password, bearerToken, useOcsApiRequest
                ), useOcsApiRequest
        );
    }

    public ServerResponse.CreateRemoteProjectResponse createAnonymousRemoteProject(CustomCertManager ccm, DBProject project) throws IOException, NextcloudHttpRequestFailedException {
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

    public ServerResponse.CreateRemoteProjectResponse createAuthenticatedRemoteProject(CustomCertManager ccm, DBProject project) throws IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
        // request values
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

        String target;
        String username = null;
        String password = null;
        boolean useOcsApiRequest = false;
        // use SSO
        if (ssoAccount != null) {
            if (this.cospendVersionGT161) {
                target = "/ocs/v2.php/apps/cospend/api/v1/projects";
                Log.i(TAG, "using new API for createAuthenticatedRemoteProject");
                return new ServerResponse.CreateRemoteProjectResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_POST, paramKeys, paramValues, true), true);
            } else {
                target = "/index.php/apps/cospend/api-priv/projects";
                return new ServerResponse.CreateRemoteProjectResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_POST, paramKeys, paramValues, false), false);
            }
        } else {
            // use NC login/passwd
            username = this.username;
            password = this.password;
            target = this.cospendVersionGT161
                    ? project.getRequestBaseUrl(true) + "/api/v1/projects"
                    : project.getRequestBaseUrl(false) + "/api-priv/projects";
            useOcsApiRequest = this.cospendVersionGT161;
            if (this.cospendVersionGT161) {
                Log.i(TAG, "using new API (weblogin, " + username + ":" + password + ") for createAuthenticatedRemoteProject");
            }
            return new ServerResponse.CreateRemoteProjectResponse(
                    requestServer(
                            ccm, target, METHOD_POST, paramKeys, paramValues,
                            null, username, password, null, useOcsApiRequest
                    ), useOcsApiRequest
            );
        }
    }

    public ServerResponse.CreateRemoteBillResponse createRemoteBill(CustomCertManager ccm, DBProject project, DBBill bill, Map<Long, Long> memberIdToRemoteId) throws IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        // we keep sending date for IHateMoney and old Cospend versions
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

        String target;
        String username = null;
        String password = null;
        String bearerToken = null;
        boolean useOcsApiRequest = false;
        if (ProjectType.COSPEND.equals(project.getType())) {
            paramKeys.add("comment");
            paramValues.add(bill.getComment());
            paramKeys.add("timestamp");
            paramValues.add(String.valueOf(bill.getTimestamp()));
            paramKeys.add("repeat");
            paramValues.add(bill.getRepeat());
            if (this.cospendVersionGT161) {
                paramKeys.add("payedFor");
                paramKeys.add("paymentMode");
                paramKeys.add("categoryId");
                paramKeys.add("paymentModeId");
            } else {
                paramKeys.add("payed_for");
                paramKeys.add("paymentmode");
                paramKeys.add("categoryid");
                paramKeys.add("paymentmodeid");
            }
            String payedFor = "";
            for (long boId : bill.getBillOwersIds()) {
                payedFor += String.valueOf(memberIdToRemoteId.get(boId)) + ",";
            }
            payedFor = payedFor.replaceAll(",$", "");
            paramValues.add(payedFor);
            paramValues.add(bill.getPaymentMode());
            paramValues.add(String.valueOf(bill.getCategoryRemoteId()));
            paramValues.add(String.valueOf(bill.getPaymentModeRemoteId()));

            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = this.cospendVersionGT161
                        ? project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.getRemoteId() + "/bills"
                        : project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.getRemoteId() + "/bills";
                useOcsApiRequest = this.cospendVersionGT161;
                if (this.cospendVersionGT161) {
                    Log.i(TAG, "using new API (weblogin, " + username + ":" + password + ") for createRemoteBill");
                }
            } else if (canAccessProjectWithSSO(project)) {
                if (this.cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.getRemoteId() + "/bills";
                    Log.i(TAG, "using new API for createRemoteBill");
                    return new ServerResponse.CreateRemoteBillResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_POST, paramKeys, paramValues, true), true);
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/bills";
                    return new ServerResponse.CreateRemoteBillResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_POST, paramKeys, paramValues, false), false);
                }
            } else {
                useOcsApiRequest = this.cospendVersionGT161;
                target = this.cospendVersionGT161
                    ? project.getRequestBaseUrl(true) + "/api/v1/public/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/bills"
                    : project.getRequestBaseUrl(false) + "/api/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/bills";

                Log.i(TAG, "using public API, target is: "+target+"for createRemoteBill");
            }
        } else {
            target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId() + "/bills";
            username = project.getRemoteId();
            password = project.getPassword();
            bearerToken = project.getBearerToken();

            for (long boId : bill.getBillOwersIds()) {
                paramKeys.add("payed_for");
                paramValues.add(
                        String.valueOf(
                                memberIdToRemoteId.get(boId)
                        )
                );
            }
        }

        return new ServerResponse.CreateRemoteBillResponse(
                requestServer(
                        ccm, target, METHOD_POST, paramKeys, paramValues, null,
                        username, password, bearerToken, useOcsApiRequest
                ), useOcsApiRequest
        );
    }

    public ServerResponse.CreateRemoteMemberResponse createRemoteMember(CustomCertManager ccm, DBProject project, DBMember member) throws IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("name");
        paramValues.add(member.getName());

        String target;
        String username = null;
        String password = null;
        String bearerToken = null;
        boolean useOcsApiRequest = false;
        if (ProjectType.COSPEND.equals(project.getType())) {
            // put color if set
            Integer r = member.getR();
            Integer g = member.getG();
            Integer b = member.getB();
            if (r != null && g != null && b != null) {
                String hexColor = "#" + Integer.toHexString(r) + Integer.toHexString(g) + Integer.toHexString(b);
                paramKeys.add("color");
                paramValues.add(hexColor);
            }
            // launch the request
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = this.cospendVersionGT161
                        ? project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.getRemoteId() + "/members"
                        : project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.getRemoteId() + "/members";
                useOcsApiRequest = this.cospendVersionGT161;
                if (this.cospendVersionGT161) {
                    Log.i(TAG, "using new API (weblogin, " + username + ":" + password + ") for createRemoteMember");
                }
            } else if (canAccessProjectWithSSO(project)) {
                if (this.cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.getRemoteId() + "/members";
                    Log.i(TAG, "using new API for createRemoteBill");
                    return new ServerResponse.CreateRemoteMemberResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_POST, paramKeys, paramValues, true), true, true);
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/members";
                    return new ServerResponse.CreateRemoteMemberResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_POST, paramKeys, paramValues, false), false, false);
                }
            } else {
                useOcsApiRequest = this.cospendVersionGT161;
                target = this.cospendVersionGT161
                    ? project.getRequestBaseUrl(true) + "/api/v1/public/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/members"
                    : project.getRequestBaseUrl(false) + "/api/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/members";

                Log.i(TAG, "using public API, target is: "+target+"for createRemoteBill");
            }
        } else {
            target = project.getServerUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/members";
            username = project.getRemoteId();
            password = project.getPassword();
            bearerToken = project.getBearerToken();
        }

        return new ServerResponse.CreateRemoteMemberResponse(
                requestServer(
                        ccm, target, METHOD_POST, paramKeys, paramValues, null,
                        username, password, bearerToken, useOcsApiRequest
                ), useOcsApiRequest,
                ProjectType.COSPEND.equals(project.getType()) && this.cospendVersionGT161
        );
    }

    public ServerResponse.BillsResponse getBills(CustomCertManager ccm, DBProject project) throws JSONException, IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
        String target;
        String username = null;
        String password = null;
        String bearerToken = null;
        boolean useOcsApiRequest = false;
        if (ProjectType.COSPEND.equals(project.getType())) {
            Long tsLastSync = project.getLastSyncedTimestamp();
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = this.cospendVersionGT161
                    ? project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.getRemoteId() + "/bills?lastChanged=" + tsLastSync
                    : project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.getRemoteId() + "/bills?lastchanged=" + tsLastSync;
                useOcsApiRequest = this.cospendVersionGT161;
                if (this.cospendVersionGT161) {
                    Log.i(TAG, "using new API (weblogin, " + username + ":" + password + ") for getBills");
                }
                return new ServerResponse.BillsResponse(
                        requestServer(
                                ccm, target, METHOD_GET, null, null,
                                null, username, password, null, useOcsApiRequest
                        ),
                        useOcsApiRequest
                );
            } else if (canAccessProjectWithSSO(project)) {
                List<String> paramKeys = new ArrayList<>();
                List<String> paramValues = new ArrayList<>();
                if (this.cospendVersionGT161) {
                    paramKeys.add("lastChanged");
                } else {
                    paramKeys.add("lastchanged");
                }
                paramValues.add(String.valueOf(tsLastSync));
                if (this.cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.getRemoteId() + "/bills";
                    Log.i(TAG, "using new API for getBills");
                    return new ServerResponse.BillsResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, paramKeys, paramValues, true), true);
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/bills";
                    return new ServerResponse.BillsResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, paramKeys, paramValues, false), false);
                }
            } else {
                useOcsApiRequest = this.cospendVersionGT161;
                target = this.cospendVersionGT161
                    ? project.getRequestBaseUrl(true) + "/api/v1/public/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/bills?lastChanged=" + tsLastSync
                    : project.getRequestBaseUrl(false) + "/apiv2/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/bills?lastchanged=" + tsLastSync;

                Log.i(TAG, "using public API, target is: "+target+"for getBills");
                return new ServerResponse.BillsResponse(
                        requestServer(
                                ccm, target, METHOD_GET, null, null,
                                null, username, password, null, useOcsApiRequest
                        ),
                        useOcsApiRequest
                );
            }
        } else {
            target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId() + "/bills";
            username = project.getRemoteId();
            password = project.getPassword();
            bearerToken = project.getBearerToken();
            return new ServerResponse.BillsResponse(
                    requestServer(
                            ccm, target, METHOD_GET, null, null,
                            null, username, password, bearerToken, false
                    ),
                    false
            );
        }
    }

    public ServerResponse.MembersResponse getMembers(CustomCertManager ccm, DBProject project) throws JSONException, IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
        String target;
        String username = null;
        String password = null;
        String bearerToken = null;
        boolean useOcsApiRequest = false;
        if (ProjectType.COSPEND.equals(project.getType())) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = this.cospendVersionGT161
                        ? project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.getRemoteId() + "/members"
                        : project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.getRemoteId() + "/members";
                useOcsApiRequest = this.cospendVersionGT161;
                if (this.cospendVersionGT161) {
                    Log.i(TAG, "using new API (weblogin, " + username + ":" + password + ") for getMembers, projectId: " + project.getRemoteId());
                }
            } else if (canAccessProjectWithSSO(project)) {
                if (this.cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.getRemoteId() + "/members";
                    Log.i(TAG, "using new API for getMembers");
                    return new ServerResponse.MembersResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, null, null, true), true);
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/members";
                    return new ServerResponse.MembersResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, null, null, false), false);
                }
            } else {
                useOcsApiRequest = this.cospendVersionGT161;
                target = this.cospendVersionGT161
                    ? project.getRequestBaseUrl(true) + "/api/v1/public/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/members"
                    : project.getRequestBaseUrl(false) + "/api/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/members";

                Log.i(TAG, "using public API, target is: "+target+"for getMembers");
            }
        } else {
            target = project.getServerUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId() + "/members";
            username = project.getRemoteId();
            password = project.getPassword();
            bearerToken = project.getBearerToken();
        }
        return new ServerResponse.MembersResponse(
                requestServer(
                        ccm, target, METHOD_GET, null, null,
                        null, username, password, bearerToken, useOcsApiRequest
                ), useOcsApiRequest
        );
    }

    public ServerResponse.CreateRemoteCurrencyResponse createRemoteCurrency(CustomCertManager ccm, DBProject project, DBCurrency currency) throws IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("name");
        paramValues.add(currency.getName());
        paramKeys.add("rate");
        paramValues.add(String.valueOf(currency.getExchangeRate()));

        String target;
        String username = null;
        String password = null;
        String bearerToken = null;
        boolean useOcsApiRequest = false;
        if (ProjectType.COSPEND.equals(project.getType())) {
            // launch the request
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = this.cospendVersionGT161
                        ? project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.getRemoteId() + "/currency"
                        : project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.getRemoteId() + "/currency";
                useOcsApiRequest = this.cospendVersionGT161;
                if (this.cospendVersionGT161) {
                    Log.i(TAG, "using new API (weblogin, " + username + ":" + password + ") for createRemoteCurrency");
                }
            } else if (canAccessProjectWithSSO(project)) {
                if (this.cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.getRemoteId() + "/currency";
                    Log.i(TAG, "using new API for createRemoteCurrency");
                    return new ServerResponse.CreateRemoteCurrencyResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_POST, paramKeys, paramValues, true), true);
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/currency";
                    return new ServerResponse.CreateRemoteCurrencyResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_POST, paramKeys, paramValues, false), false);
                }
            } else {
                useOcsApiRequest = this.cospendVersionGT161;
                target = this.cospendVersionGT161
                    ? project.getRequestBaseUrl(true) + "/api/v1/public/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/currency"
                    : project.getRequestBaseUrl(false) + "/api/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/currency";

                Log.i(TAG, "using public API, target is: "+target+"for createRemoteCurrency");
            }
        } else {
            target = project.getServerUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/currency";
            username = project.getRemoteId();
            password = project.getPassword();
            bearerToken = project.getBearerToken();
        }

        return new ServerResponse.CreateRemoteCurrencyResponse(
                requestServer(
                        ccm, target, METHOD_POST, paramKeys, paramValues, null,
                        username, password, bearerToken, useOcsApiRequest
                ), useOcsApiRequest
        );
    }

    public ServerResponse.EditRemoteCurrencyResponse editRemoteCurrency(CustomCertManager ccm, DBProject project, DBCurrency currency) throws IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("name");
        paramValues.add(currency.getName());
        paramKeys.add("rate");
        paramValues.add(String.valueOf(currency.getExchangeRate()));


        String target;
        String username = null;
        String password = null;
        String bearerToken = null;
        boolean useOcsApiRequest = false;
        if (ProjectType.COSPEND.equals(project.getType())) {

            // launch the request
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = this.cospendVersionGT161
                        ? project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.getRemoteId() + "/currency/" + currency.getRemoteId()
                        : project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.getRemoteId() + "/currency/" + currency.getRemoteId();
                useOcsApiRequest = this.cospendVersionGT161;
                if (this.cospendVersionGT161) {
                    Log.i(TAG, "using new API (weblogin, " + username + ":" + password + ") for editRemoteCurrency");
                }
            } else if (canAccessProjectWithSSO(project)) {
                if (this.cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.getRemoteId() + "/currency/" + currency.getRemoteId();
                    Log.i(TAG, "using new API for createRemoteCurrency");
                    return new ServerResponse.EditRemoteCurrencyResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_PUT, paramKeys, paramValues, true), true);
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/currency/" + currency.getRemoteId();
                    return new ServerResponse.EditRemoteCurrencyResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_PUT, paramKeys, paramValues, false), false);
                }
            } else {
                useOcsApiRequest = this.cospendVersionGT161;
                target = this.cospendVersionGT161
                    ? project.getRequestBaseUrl(true) + "/api/v1/public/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/currency/" + currency.getRemoteId()
                    : project.getRequestBaseUrl(false) + "/api/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/currency/" + currency.getRemoteId();

                Log.i(TAG, "using public API, target is: "+target+"for createRemoteCurrency");
            }
        } else {
            target = project.getServerUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/currency/" + currency.getRemoteId();
            username = project.getRemoteId();
            password = project.getPassword();
            bearerToken = project.getBearerToken();
        }

        return new ServerResponse.EditRemoteCurrencyResponse(
                requestServer(
                        ccm, target, METHOD_PUT, paramKeys, paramValues, null,
                        username, password, bearerToken, useOcsApiRequest
                ), useOcsApiRequest
        );
    }

    public ServerResponse.DeleteRemoteCurrencyResponse deleteRemoteCurrency(CustomCertManager ccm, DBProject project, long currencyRemoteId) throws IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
        String target;
        String username = null;
        String password = null;
        String bearerToken = null;
        boolean useOcsApiRequest = false;
        if (ProjectType.COSPEND.equals(project.getType())) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = this.cospendVersionGT161
                        ? project.getRequestBaseUrl(true) + "/api/v1/projects/" + project.getRemoteId() + "/currency/" + currencyRemoteId
                        : project.getRequestBaseUrl(false) + "/api-priv/projects/" + project.getRemoteId() + "/currency/" + currencyRemoteId;
                useOcsApiRequest = this.cospendVersionGT161;
                if (this.cospendVersionGT161) {
                    Log.i(TAG, "using new API (weblogin, " + username + ":" + password + ") for deleteRemoteCurrency");
                }
            } else if (canAccessProjectWithSSO(project)) {
                if (this.cospendVersionGT161) {
                    target = "/ocs/v2.php/apps/cospend/api/v1/projects/" + project.getRemoteId() + "/currency/" + currencyRemoteId;
                    Log.i(TAG, "using new API for deleteRemoteCurrency");
                    return new ServerResponse.DeleteRemoteCurrencyResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_PUT, null, null, true), true);
                } else {
                    target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/currency/" + currencyRemoteId;
                    return new ServerResponse.DeleteRemoteCurrencyResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_PUT, null, null, false), false);
                }
            } else {
                useOcsApiRequest = this.cospendVersionGT161;
                target = this.cospendVersionGT161
                    ? project.getRequestBaseUrl(true) + "/api/v1/public/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/currency/" + currencyRemoteId
                    : project.getRequestBaseUrl(false) + "/api/projects/"
                        + project.getRemoteId() + "/" + getEncodedPassword(project.getPassword()) + "/currency/" + currencyRemoteId;

                Log.i(TAG, "using public API, target is: "+target+"for deleteRemoteCurrency");
            }
        } else {
            target = project.getServerUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/currency/" + currencyRemoteId;
            username = project.getRemoteId();
            password = project.getPassword();
            bearerToken = project.getBearerToken();
        }
        return new ServerResponse.DeleteRemoteCurrencyResponse(
                requestServer(
                        ccm, target, METHOD_DELETE, null, null,
                        null, username, password, bearerToken, useOcsApiRequest
                ), useOcsApiRequest
        );
    }

    private ResponseData requestServerWithSSO(NextcloudAPI nextcloudAPI, String target, String method, List<String> paramKeys, List<String> paramValues, boolean isOCSRequest) throws TokenMismatchException, NextcloudHttpRequestFailedException {
        StringBuffer result = new StringBuffer();

        List<QueryParam> params = null;
        if (paramKeys != null && paramValues != null) {
            params = new ArrayList<>();
            for (int i = 0; i < paramKeys.size(); i++) {
                String key = paramKeys.get(i);
                String value = paramValues.get(i);
                params.add(new QueryParam(key, value));
            }
        }

        Map<String, List<String>> headers = new HashMap<>();
        if (isOCSRequest) {
            List<String> acceptHeader = new ArrayList<>();
            acceptHeader.add("application/json");
            headers.put("Accept", acceptHeader);
        }

        NextcloudRequest nextcloudRequest;
        if (params == null) {
            nextcloudRequest = new NextcloudRequest.Builder()
                    .setMethod(method)
                    .setUrl(target)
                    .setHeader(headers)
                    .build();
        } else {
            nextcloudRequest = new NextcloudRequest.Builder()
                    .setMethod(method)
                    .setUrl(target)
                    .setParameter(params)
                    .setHeader(headers)
                    .build();
        }

        try {
            // InputStream inputStream = nextcloudAPI.performNetworkRequest(nextcloudRequest);
            Response response = nextcloudAPI.performNetworkRequestV2(nextcloudRequest);
            InputStream inputStream = response.getBody();

            BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            Log.d(getClass().getSimpleName(), "RES versatile " + result.toString());
            inputStream.close();
        } catch (TokenMismatchException e) {
            Log.d(getClass().getSimpleName(), "Mismatcho SSO server request error " + e.toString());
            /*try {
                SingleAccountHelper.reauthenticateCurrentAccount(:smile:);
            } catch (NextcloudFilesAppAccountNotFoundException | NoCurrentAccountSelectedException | NextcloudFilesAppNotSupportedException ee) {
                UiExceptionManager.showDialogForException(new SettingsActivity(), ee);
            } catch (NextcloudFilesAppAccountPermissionNotGrantedException ee) {
                // Unable to reauthenticate account just like that..
                // TODO Show login screen here
                LoginDialogFragment loginDialogFragment = new LoginDialogFragment();
                loginDialogFragment.show(new SettingsActivity().getSupportFragmentManager(), "NoticeDialogFragment");
            }*/
            throw e;
        } catch (NextcloudHttpRequestFailedException e) {
            Log.d(getClass().getSimpleName(), "SSO server HTTP request failed "+e.getStatusCode());
            throw e;
        } catch (Exception e) {
            // TODO handle errors
            Log.d(getClass().getSimpleName(), "SSO server request error " + e.toString());
        }

        return new VersatileProjectSyncClient.ResponseData(result.toString(), "", 0);
    }

    /**
     * Request-Method for POST, PUT with or without JSON-Object-Parameter
     *
     * @param target Filepath to the wanted function
     * @param method GET, POST, DELETE or PUT
     * @return Body of answer
     * @throws MalformedURLException
     * @throws IOException
     */
    private ResponseData requestServer(CustomCertManager ccm, String target, String method,
                                       List<String> paramKeys, List<String> paramValues,
                                       String lastETag, String username, String password,
                                       @Nullable String bearerToken, boolean isOCSRequest)
            throws IOException, NextcloudHttpRequestFailedException {
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
        byte[] paramData = null;
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
            throw new NextcloudHttpRequestFailedException(context, responseCode, new IOException(result.toString()));
        }
        // create response object
        String etag = con.getHeaderField("ETag");
        long lastModified = con.getHeaderFieldDate("Last-Modified", 0) / 1000;
        Log.i(TAG, "Result length:  " + result.length() + (paramData == null ? "" : "; Request length: " + paramData.length));
        Log.d(TAG, "ETag: " + etag + "; Last-Modified: " + lastModified + " (" + con.getHeaderField("Last-Modified") + ")");
        // return these header fields since they should only be saved after successful processing the result!
        return new ResponseData(result.toString(), etag, lastModified);
    }
}
