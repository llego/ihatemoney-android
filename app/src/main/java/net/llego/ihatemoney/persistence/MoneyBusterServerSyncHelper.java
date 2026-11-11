package net.llego.ihatemoney.persistence;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.android.activity.BillsListViewActivity;
import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.model.DBBillOwer;
import net.llego.ihatemoney.model.DBCategory;
import net.llego.ihatemoney.model.DBCurrency;
import net.llego.ihatemoney.model.DBMember;
import net.llego.ihatemoney.model.DBPaymentMode;
import net.llego.ihatemoney.model.DBProject;
import net.llego.ihatemoney.model.ProjectType;
import net.llego.ihatemoney.util.ICallback;
import net.llego.ihatemoney.util.IProjectCreationCallback;
import net.llego.ihatemoney.util.VersatileProjectSyncClient;
import net.llego.ihatemoney.util.ServerResponse;
import net.llego.ihatemoney.util.SpliitClient;
import net.llego.ihatemoney.util.SupportUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import at.bitfire.cert4android.CustomCertManager;


/**
 * Helps to synchronize the Database to the Server.
 */
public class MoneyBusterServerSyncHelper {

    private static final String TAG = MoneyBusterServerSyncHelper.class.getSimpleName();

    public static final String BROADCAST_PROJECT_SYNC_FAILED = "net.llego.ihatemoney.broadcast.project_sync_failed";
    public static final String BROADCAST_PROJECT_SYNCED = "net.llego.ihatemoney.broadcast.project_synced";
    public static final String BROADCAST_SYNC_PROJECT = "net.llego.ihatemoney.broadcast.sync_project";
    public static final String BROADCAST_NETWORK_AVAILABLE = "net.llego.ihatemoney.broadcast.network_available";
    public static final String BROADCAST_NETWORK_UNAVAILABLE = "net.llego.ihatemoney.broadcast.network_unavailable";
    public static final String BROADCAST_AVATAR_UPDATED = "net.llego.ihatemoney.broadcast.avatar_updated";
    public static final String BROADCAST_AVATAR_UPDATED_MEMBER = "net.llego.ihatemoney.broadcast.avatar_updated_for_member";

    private static int NOTIFICATION_ID = 1526756699;
    public static final int MAIN_CHANNEL_ID = 1234567890;

    private final SharedPreferences preferences;

    private static MoneyBusterServerSyncHelper instance;

    /**
     * Get (or create) instance from MoneyBusterServerSyncHelper.
     * This has to be a singleton in order to realize correct registering and unregistering of
     * the BroadcastReceiver, which listens on changes of network connectivity.
     *
     * @param dbHelper MoneyBusterSQLiteOpenHelper
     * @return MoneyBusterServerSyncHelper
     */
    public static synchronized MoneyBusterServerSyncHelper getInstance(MoneyBusterSQLiteOpenHelper dbHelper) {
        if (instance == null) {
            instance = new MoneyBusterServerSyncHelper(dbHelper);
        }
        return instance;
    }

    private final MoneyBusterSQLiteOpenHelper dbHelper;
    private final Context appContext;

    private final CustomCertManager customCertManager;

    private boolean networkConnected = false;

    // current state of the synchronization
    private boolean syncActive = false;
    private static final List<Long> projectIdsToSync = new ArrayList<>();

    // list of callbacks for both parts of synchronziation
    private List<ICallback> callbacksPush = new ArrayList<>();
    private List<ICallback> callbacksPull = new ArrayList<>();

    private MoneyBusterServerSyncHelper(MoneyBusterSQLiteOpenHelper db) {
        this.dbHelper = db;
        this.appContext = db.getContext().getApplicationContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(dbHelper.getContext());
        customCertManager = SupportUtil.getCertManager(appContext);

        updateNetworkStatus();
    }

    /**
     * Synchronization is only possible if there is an active network connection.
     * MoneyBusterServerSyncHelper observes changes in the network connection.
     * The current state can be retrieved with this method.
     *
     * @return true if sync is possible, otherwise false.
     */
    public boolean isSyncPossible() {
        updateNetworkStatus();
        return networkConnected;
    }

    public CustomCertManager getCustomCertManager() {
        return customCertManager;
    }

    /**
     * Adds a callback method to the MoneyBusterServerSyncHelper for the synchronization part push local changes to the server.
     * All callbacks will be executed once the synchronization operations are done.
     * After execution the callback will be deleted, so it has to be added again if it shall be
     * executed the next time all synchronize operations are finished.
     *
     * @param callback Implementation of ICallback, contains one method that shall be executed.
     */
    public void addCallbackPush(ICallback callback) {
        callbacksPush.add(callback);
    }

    /**
     * Adds a callback method to the MoneyBusterServerSyncHelper for the synchronization part pull remote changes from the server.
     * All callbacks will be executed once the synchronization operations are done.
     * After execution the callback will be deleted, so it has to be added again if it shall be
     * executed the next time all synchronize operations are finished.
     *
     * @param callback Implementation of ICallback, contains one method that shall be executed.
     */
    public void addCallbackPull(ICallback callback) {
        callbacksPull.add(callback);
    }


    /**
     * Schedules a synchronization and start it directly, if the network is connected and no
     * synchronization is currently running.
     *
     * @param onlyLocalChanges Whether to only push local changes to the server or to also load the project info
     */
    public @Nullable SyncTask scheduleSync(boolean onlyLocalChanges, long projId) {
        Log.d(getClass().getSimpleName(), "Sync requested (" + (onlyLocalChanges ? "onlyLocalChanges" : "full") + "; " + (syncActive ? "sync active" : "sync NOT active") + ") ...");
        Log.d(getClass().getSimpleName(), "(network:" + networkConnected + ")");
        updateNetworkStatus();
        if (isSyncPossible() && (!syncActive || onlyLocalChanges)) {
            DBProject project = dbHelper.getProject(projId);
            if (project != null) {
                Log.d(getClass().getSimpleName(), "... starting now");
                SyncTask syncTask = new SyncTask(onlyLocalChanges, project);
                syncTask.addCallbacks(callbacksPush);
                callbacksPush = new ArrayList<>();
                if (!onlyLocalChanges) {
                    syncTask.addCallbacks(callbacksPull);
                    callbacksPull = new ArrayList<>();
                }
                return (SyncTask) syncTask.execute();
            } else {
                Log.d(getClass().getSimpleName(), "sync asked for project " + projId + " which does not exist : DOING NOTHING");
            }
        } else if (!onlyLocalChanges) {
            Log.d(getClass().getSimpleName(), "... scheduled");
            if (!projectIdsToSync.contains(projId)) {
                projectIdsToSync.add(projId);
            }
            for (ICallback callback : callbacksPush) {
                callback.onScheduled();
            }
        } else {
            Log.d(getClass().getSimpleName(), "... do nothing");
            for (ICallback callback : callbacksPush) {
                callback.onScheduled();
            }
        }
        return null;
    }

    private void updateNetworkStatus() {
        ConnectivityManager connMgr = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeInfo = connMgr.getActiveNetworkInfo();
        if (activeInfo != null && activeInfo.isConnected()) {
            Log.d(MoneyBusterServerSyncHelper.class.getSimpleName(), "Network connection established.");
            networkConnected = true;
        } else {
            networkConnected = false;
            Log.d(MoneyBusterServerSyncHelper.class.getSimpleName(), "No network connection.");
        }
    }

    /**
     * SyncTask is an AsyncTask which performs the synchronization in a background thread.
     * Synchronization consists of two parts: pushLocalChanges and pullRemoteChanges.
     */
    public class SyncTask extends AsyncTask<Void, Void, Boolean> {
        private final boolean onlyLocalChanges;
        private final DBProject project;
        private final List<ICallback> callbacks = new ArrayList<>();
        private VersatileProjectSyncClient client;
        private List<Throwable> exceptions = new ArrayList<>();
        private List<String> errorMessages = new ArrayList<>();
        private int nbPulledNewBills = 0;
        private int nbPulledUpdatedBills = 0;
        private int nbPulledDeletedBills = 0;
        private final StringBuilder newBillsDialogText = new StringBuilder();
        private final StringBuilder updatedBillsDialogText = new StringBuilder();
        private final StringBuilder deletedBillsDialogText = new StringBuilder();

        public SyncTask(boolean onlyLocalChanges, DBProject project) {
            this.onlyLocalChanges = onlyLocalChanges;
            Log.i(getClass().getSimpleName(), "SYNC TASK project : " + project.getRemoteId());
            this.project = project;

            createNotificationChannels(appContext, dbHelper);
        }

        public void addCallbacks(List<ICallback> callbacks) {
            this.callbacks.addAll(callbacks);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            syncActive = true;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            Log.i(getClass().getSimpleName(), "STARTING SYNCHRONIZATION");

            if (project.isReadOnly()) {
                // Spliit is read-only: pull the group and never push local changes.
                return pullSpliitGroup();
            }

            client = createVersatileProjectSyncClient();
            //dbHelper.debugPrintFullDB();
            boolean status = pushLocalChanges();
            if (status && !onlyLocalChanges) {
                status = pullRemoteChanges();
            }
            //dbHelper.debugPrintFullDB();
            Log.i(getClass().getSimpleName(), "SYNCHRONIZATION FINISHED");
            return status;
        }

        private boolean pushLocalChanges() {
            Log.d(getClass().getSimpleName(), "PUSH LOCAL CHANGES");

            try {
                List<DBMember> membersToAdd = dbHelper.getMembersOfProjectWithState(project.getId(), DBBill.STATE_ADDED);
                // Only fetch remote members when resolving a newly added member's name conflict.
                List<DBMember> remoteMembers = new ArrayList<>();
                if (!membersToAdd.isEmpty()) {
                    ServerResponse.MembersResponse membersResponse = client.getMembers(customCertManager, project);
                    remoteMembers = membersResponse.getMembers(project.getId());
                }
                List<String> remoteMembersNames = new ArrayList<>();
                for (DBMember m : remoteMembers) {
                    remoteMembersNames.add(m.getName());
                }
                // push member changes
                // add members
                for (DBMember mToAdd : membersToAdd) {
                    // it exists remotely, just update it locally to solve conflict
                    int searchIndex = remoteMembersNames.indexOf(mToAdd.getName());
                    if (searchIndex != -1) {
                        DBMember remoteMember = remoteMembers.get(searchIndex);
                        dbHelper.updateMember(
                                mToAdd.getId(), null,
                                remoteMember.getWeight(), remoteMember.isActivated(),
                                DBBill.STATE_OK, remoteMember.getRemoteId(), remoteMember.getR(),
                                remoteMember.getG(), remoteMember.getB(),
                                remoteMember.getNcUserId(), ""
                        );
                    } else {
                        // it does not exist, create it remotely
                        ServerResponse.CreateRemoteMemberResponse createRemoteMemberResponse = client.createRemoteMember(customCertManager, project, mToAdd);
                        long newRemoteId = createRemoteMemberResponse.getRemoteMemberId();
                        if (newRemoteId > 0) {
                            dbHelper.updateMember(
                                mToAdd.getId(), null,
                                null, null, DBBill.STATE_OK, newRemoteId,
                                null, null, null, null, null
                            );
                        }
                    }
                }

                // edit members
                List<DBMember> membersToEdit = dbHelper.getMembersOfProjectWithState(project.getId(), DBBill.STATE_EDITED);
                for (DBMember mToEdit : membersToEdit) {
                    try {
                        ServerResponse.EditRemoteMemberResponse editRemoteMemberResponse = client.editRemoteMember(customCertManager, project, mToEdit);
                        long remoteId = editRemoteMemberResponse.getRemoteId(project.getId());
                        if (remoteId == mToEdit.getRemoteId()) {
                            dbHelper.updateMember(
                                    mToEdit.getId(), null,
                                    null, null, DBBill.STATE_OK, null,
                                    null, null, null, null, null
                            );
                        }
                    } catch (IOException e) {
                        if (e.getMessage().equals("{\"message\": \"Internal Server Error\"}")) {
                            Log.d(getClass().getSimpleName(), "EDIT MEMBER FAILED : it does not exist remotely");
                            // what if member does not exist anymore :
                            // pullremotechanges will delete it locally (if no bill associated)
                            // but maybe there still are bills for this user,
                            // wait untill the end of pullremotechanges to check
                            // and delete useless members
                        } else {
                            throw e;
                        }
                    }
                }

                // get members
                List<DBMember> members = dbHelper.getMembersOfProject(project.getId(), null);
                // get member id map
                Map<Long, Long> memberIdToRemoteId = new HashMap<>();
                for (DBMember m : members) {
                    memberIdToRemoteId.put(m.getId(), m.getRemoteId());
                }

                // delete what's been deleted
                List<DBBill> toDelete = dbHelper.getBillsOfProjectWithState(project.getId(), DBBill.STATE_DELETED);
                for (DBBill bToDel : toDelete) {
                    try {
                        ServerResponse.DeleteRemoteBillResponse deleteRemoteBillResponse = client.deleteRemoteBill(customCertManager, project, bToDel.getRemoteId());
                        if (deleteRemoteBillResponse.getStringContent().equals("OK")) {
                            Log.d(TAG, "successfully deleted bill on remote project : delete it locally");
                            dbHelper.deleteBill(bToDel.getId());
                        }
                    } catch (IOException e) {
                        // the bill does not exist in the remote project
                        if (e.getMessage().equals("\"Not Found\"")) {
                            Log.d(TAG, "failed to delete bill on remote project : delete it locally anyway");
                            dbHelper.deleteBill(bToDel.getId());
                        } else {
                            throw e;
                        }
                    }
                }
                // edit what's been edited
                List<DBBill> toEdit = dbHelper.getBillsOfProjectWithState(project.getId(), DBBill.STATE_EDITED);
                for (DBBill bToEdit : toEdit) {
                    try {
                        ServerResponse.EditRemoteBillResponse editRemoteBillResponse = client.editRemoteBill(customCertManager, project, bToEdit, memberIdToRemoteId);
                        if (editRemoteBillResponse.getStringContent().equals(String.valueOf(bToEdit.getRemoteId()))) {
                            dbHelper.setBillState(bToEdit.getId(), DBBill.STATE_OK);
                            Log.d(getClass().getSimpleName(), "SUCCESSFUL remote bill edition (" + editRemoteBillResponse.getStringContent() + ")");
                        } else {
                            Log.d(getClass().getSimpleName(), "FAILED to edit remote bill (" + editRemoteBillResponse.getStringContent() + ")");
                        }
                    } catch (IOException e) {
                        Log.d(getClass().getSimpleName(), "FAILED to edit remote bill: it probably does not exist remotely");
                        // pullremotechanges will take care of deletion if it's not there anymore
                    }
                }
                // add what's been added
                List<DBBill> toAdd = dbHelper.getBillsOfProjectWithState(project.getId(), DBBill.STATE_ADDED);
                for (DBBill bToAdd : toAdd) {
                    ServerResponse.CreateRemoteBillResponse createRemoteBillResponse = client.createRemoteBill(customCertManager, project, bToAdd, memberIdToRemoteId);
                    long newRemoteId = Long.parseLong(createRemoteBillResponse.getStringContent());
                    if (newRemoteId > 0) {
                        dbHelper.updateBill(
                                bToAdd.getId(), newRemoteId, null,
                                null, null, null,
                                DBBill.STATE_OK, null,
                                null, null,
                                null, null
                        );
                    }
                }

                // Handle currencies
                // delete what's been deleted
                List<DBCurrency> currenciesToDelete = dbHelper.getCurrenciesOfProjectWithState(project.getId(), DBBill.STATE_DELETED);
                for (DBCurrency cToDel : currenciesToDelete) {
                    try {
                        ServerResponse.DeleteRemoteCurrencyResponse deleteRemoteCurrencyResponse = client.deleteRemoteCurrency(customCertManager, project, cToDel.getRemoteId());
                        if (deleteRemoteCurrencyResponse.getStringContent().equals("OK")) {
                            Log.d(getClass().getSimpleName(), "successfully deleted currency on remote project : delete it locally");
                            dbHelper.deleteCurrency(cToDel.getId());
                        }
                    } catch (IOException e) {
                        // if it's not there on the server
                        if (e.getMessage().equals("\"Not Found\"")) {
                            Log.d(getClass().getSimpleName(), "failed to delete currency on remote project : delete it locally anyway");
                            dbHelper.deleteCurrency(cToDel.getId());
                        } else {
                            throw e;
                        }
                    }
                }
                // edit what's been edited
                List<DBCurrency> currenciesToEdit = dbHelper.getCurrenciesOfProjectWithState(project.getId(), DBBill.STATE_EDITED);
                for (DBCurrency cToEdit : currenciesToEdit) {
                    try {
                        ServerResponse.EditRemoteCurrencyResponse editRemoteCurrencyResponse = client.editRemoteCurrency(customCertManager, project, cToEdit);
                        if (editRemoteCurrencyResponse.getStringContent().equals(String.valueOf(cToEdit.getRemoteId()))) {
                            dbHelper.setCurrencyState(cToEdit.getId(), DBBill.STATE_OK);
                            Log.d(getClass().getSimpleName(), "SUCCESSFUL remote currency edition (" + editRemoteCurrencyResponse.getStringContent() + ")");
                        } else {
                            Log.d(getClass().getSimpleName(), "FAILED to edit remote currency (" + editRemoteCurrencyResponse.getStringContent() + ")");
                        }
                    } catch (IOException e) {
                        // if it's not there on the server
                        if (e.getMessage().equals("{\"message\": \"Internal Server Error\"}")) {
                            Log.d(getClass().getSimpleName(), "FAILED to edit remote currency : it does not exist remotely");
                        } else {
                            throw e;
                        }
                    }
                }
                // add what's been added
                List<DBCurrency> currencyToAdd = dbHelper.getCurrenciesOfProjectWithState(project.getId(), DBBill.STATE_ADDED);
                for (DBCurrency cToAdd : currencyToAdd) {
                    ServerResponse.CreateRemoteCurrencyResponse createRemoteCurrencyResponse = client.createRemoteCurrency(customCertManager, project, cToAdd);
                    long newRemoteId = Long.valueOf(createRemoteCurrencyResponse.getStringContent());
                    if (newRemoteId > 0) {
                        dbHelper.setCurrencyState(cToAdd.getId(), DBBill.STATE_OK);
                    }
                }

                return true;
            } catch (ServerResponse.NotModifiedException e) {
                Log.d(getClass().getSimpleName(), "No changes, nothing to do.");
                return true;
            } catch (IOException e) {
                Log.e(getClass().getSimpleName(), "Exception", e);
                exceptions.add(e);
                return false;
            } catch (JSONException e) {
                Log.e(getClass().getSimpleName(), "Exception", e);
                exceptions.add(e);
                return false;
            }
        }

        /**
         * Pull remote Changes: update or create each remote members/bills and remove remotely deleted ones
         */
        private boolean pullRemoteChanges() {
            Log.d(TAG, "pullRemoteChanges(" + project + ")");
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(appContext);
            String lastETag = null;
            long lastModified = 0;
            try {
                ServerResponse.ProjectResponse projResponse = client.getProject(customCertManager, project, lastModified, lastETag);
                String name = projResponse.getName();
                Log.i(TAG, "AAA getProjectInfo, project name: " + name);
                String email = projResponse.getEmail();
                String currencyName = projResponse.getCurrencyName();
                boolean deletionDisabled = projResponse.getDeletionDisabled();
                int myAccessLevel = projResponse.getMyAccessLevel();
                // update project if needed
                if (project.getName() == null || project.getName().equals("")
                        || !name.equals(project.getName())
                        || project.getEmail() == null
                        || project.getEmail().equals("")
                        || project.isDeletionDisabled() != deletionDisabled
                        || project.getMyAccessLevel() != myAccessLevel
                        || (
                                (currencyName == null && project.getCurrencyName() != null) ||
                                (currencyName != null && project.getCurrencyName() == null) ||
                                (currencyName != null && !currencyName.equals(project.getCurrencyName()))
                        )
                        || !email.equals(project.getEmail())) {
                    Log.d(getClass().getSimpleName(), "update local project : " + project);
                    // this is useful to transmit correct info back to billListActivity when project was just added
                    project.setName(name);
                    project.setCurrencyName(currencyName);
                    project.setDeletionDisabled(deletionDisabled);
                    project.setMyAccessLevel(myAccessLevel);
                    dbHelper.updateProject(
                        project.getId(), name, email,
                        null, null, null,
                        currencyName, deletionDisabled, myAccessLevel, null
                    );
                }

                // get payment modes
                List<DBPaymentMode> remotePaymentModes = projResponse.getPaymentModes(project.getId());
                Map<Long, DBPaymentMode> remotePaymentModesByRemoteId = new HashMap<>();
                for (DBPaymentMode remotePaymentMode : remotePaymentModes) {
                    remotePaymentModesByRemoteId.put(remotePaymentMode.getRemoteId(), remotePaymentMode);
                }

                // add/update/delete payment modes
                for (DBPaymentMode pm : remotePaymentModes) {
                    DBPaymentMode localPaymentMode = dbHelper.getPaymentMode(pm.getRemoteId(), project.getId());
                    // pm does not exist locally, add it
                    if (localPaymentMode == null) {
                        Log.d(getClass().getSimpleName(), "Add local pm : " + pm);
                        dbHelper.addPaymentMode(pm);
                    } else {
                        // pm exists, check if needs update
                        if (pm.getName().equals(localPaymentMode.getName()) &&
                                pm.getColor().equals(localPaymentMode.getColor()) &&
                                pm.getIcon().equals(localPaymentMode.getIcon())
                        ) {
                            // alright
                            Log.d(getClass().getSimpleName(), "Nothing to do for pm : " + localPaymentMode);
                        } else {
                            Log.d(getClass().getSimpleName(), "Update local pm : " + pm);

                            dbHelper.updatePaymentMode(
                                    localPaymentMode.getId(), pm.getName(), pm.getIcon(), pm.getColor()
                            );
                        }
                    }
                }

                // delete local pms which are not there remotely
                List<DBPaymentMode> localPaymentModes = dbHelper.getPaymentModes(project.getId());
                for (DBPaymentMode localPaymentMode : localPaymentModes) {
                    if (!remotePaymentModesByRemoteId.containsKey(localPaymentMode.getRemoteId())) {
                        dbHelper.deletePaymentMode(localPaymentMode.getId());
                        Log.d(TAG, "Delete local pm : " + localPaymentMode);
                    }
                }

                // get categories
                List<DBCategory> remoteCategories = projResponse.getCategories(project.getId());
                Map<Long, DBCategory> remoteCategoriesByRemoteId = new HashMap<>();
                for (DBCategory remoteCategory : remoteCategories) {
                    remoteCategoriesByRemoteId.put(remoteCategory.getRemoteId(), remoteCategory);
                }

                // add/update/delete categories
                for (DBCategory c : remoteCategories) {
                    DBCategory localCategory = dbHelper.getCategory(c.getRemoteId(), project.getId());
                    // category does not exist locally, add it
                    if (localCategory == null) {
                        Log.d(getClass().getSimpleName(), "Add local category : " + c);
                        dbHelper.addCategory(c);
                    } else {
                        // category exists, check if needs update
                        if (c.getName().equals(localCategory.getName()) &&
                                c.getColor().equals(localCategory.getColor()) &&
                                c.getIcon().equals(localCategory.getIcon())
                        ) {
                            // alright
                            Log.d(getClass().getSimpleName(), "Nothing to do for category : " + localCategory);
                        } else {
                            Log.d(getClass().getSimpleName(), "Update local category : " + c);

                            dbHelper.updateCategory(
                                    localCategory.getId(), c.getName(), c.getIcon(), c.getColor()
                            );
                        }
                    }
                }

                // delete local categories which are not there remotely
                List<DBCategory> localCategories = dbHelper.getCategories(project.getId());
                for (DBCategory localCategory : localCategories) {
                    if (!remoteCategoriesByRemoteId.containsKey(localCategory.getRemoteId())) {
                        dbHelper.deleteCategory(localCategory.getId());
                        Log.d(TAG, "Delete local category : " + localCategory);
                    }
                }

                // get currencies
                List<DBCurrency> remoteCurrencies = projResponse.getCurrencies(project.getId());
                Map<Long, DBCurrency> remoteCurrenciesByRemoteId = new HashMap<>();
                for (DBCurrency remoteCurrency : remoteCurrencies) {
                    remoteCurrenciesByRemoteId.put(remoteCurrency.getRemoteId(), remoteCurrency);
                }

                // add/update/delete currencies
                for (DBCurrency c : remoteCurrencies) {
                    DBCurrency localCurrency = dbHelper.getCurrency(c.getRemoteId(), project.getId());
                    // currency does not exist locally, add it
                    if (localCurrency == null) {
                        Log.d(getClass().getSimpleName(), "Add local currency : " + c);
                        dbHelper.addCurrency(c);
                    } else {
                        // currency exists, check if needs update
                        if (c.getName().equals(localCurrency.getName()) &&
                                c.getExchangeRate() == localCurrency.getExchangeRate()
                        ) {
                            // alright
                            Log.d(getClass().getSimpleName(), "Nothing to do for currency : " + localCurrency);
                        } else {
                            Log.d(getClass().getSimpleName(), "Update local currency : " + c);

                            dbHelper.updateCurrency(
                                    localCurrency.getId(), c.getName(), c.getExchangeRate()
                            );
                        }
                    }
                }

                // delete local currencies which are not there remotely
                List<DBCurrency> localCurrencies = dbHelper.getCurrencies(project.getId());
                for (DBCurrency localCurrency : localCurrencies) {
                    if (!remoteCurrenciesByRemoteId.containsKey(localCurrency.getRemoteId())) {
                        dbHelper.deleteCurrency(localCurrency.getId());
                        Log.d(TAG, "Delete local currency : " + localCurrencies);
                    }
                }

                // get members
                List<DBMember> remoteMembers = projResponse.getMembers(project.getId());
                Map<Long, DBMember> remoteMembersByRemoteId = new HashMap<>();
                for (DBMember remoteMember : remoteMembers) {
                    remoteMembersByRemoteId.put(remoteMember.getRemoteId(), remoteMember);
                }

                // add/update/delete members
                for (DBMember m : remoteMembers) {
                    DBMember localMember = dbHelper.getMember(m.getRemoteId(), project.getId());
                    // member does not exist locally, add it
                    if (localMember == null) {
                        Log.d(getClass().getSimpleName(), "Add local member : " + m);
                        long mid = dbHelper.addMember(m);
                        if (m.getNcUserId() != null && !"".equals(m.getNcUserId())) {
                            updateMemberAvatar(mid);
                        }
                    } else {
                        // member exists, check if needs update
                        boolean ncUserIdChanged = (
                                (m.getNcUserId() == null && localMember.getNcUserId() != null) ||
                                (m.getNcUserId() != null && localMember.getNcUserId() == null) ||
                                (
                                        m.getNcUserId() != null && localMember.getNcUserId() != null &&
                                        !m.getNcUserId().equals(localMember.getNcUserId())
                                )
                        );
                        Log.e("PULLREMOTE", "member NC user id : "+localMember.getNcUserId()+" => "+m.getNcUserId()+" ID changed "+ncUserIdChanged);
                        if (ncUserIdChanged && m.getNcUserId() == null) {
                            m.setNcUserId("");
                        }
                        if (m.getName().equals(localMember.getName()) &&
                                m.getWeight() == localMember.getWeight() &&
                                m.isActivated() == localMember.isActivated() &&
                                (
                                        // if we get null color, then keep our local color
                                        (m.getR() == null && m.getG() == null && m.getB() == null) ||
                                        (
                                            Objects.equals(m.getR(), localMember.getR()) &&
                                            Objects.equals(m.getG(), localMember.getG()) &&
                                            Objects.equals(m.getB(), localMember.getB())
                                        )
                                ) &&
                                !ncUserIdChanged
                        ) {
                            // alright
                            Log.d(getClass().getSimpleName(), "Nothing to do for member : " + localMember);
                        } else {
                            Log.d(getClass().getSimpleName(), "Update local member : " + m);
                            Integer r = m.getR();
                            Integer g = m.getG();
                            Integer b = m.getB();
                            // don't change color if we get null from server
                            if (m.getR() == null && m.getG() == null && m.getB() == null) {
                                r = localMember.getR();
                                g = localMember.getG();
                                b = localMember.getB();
                            }
                            dbHelper.updateMember(
                                    localMember.getId(), m.getName(), m.getWeight(),
                                    m.isActivated(), null, null,
                                    r, g, b, m.getNcUserId(), null
                            );
                        }
                    }
                }

                // get up-to-date DB members
                List<DBMember> dbMembers = dbHelper.getMembersOfProject(project.getId(), null);
                // get member id map
                Map<Long, Long> memberRemoteIdToId = new HashMap<>();
                for (DBMember m : dbMembers) {
                    memberRemoteIdToId.put(m.getRemoteId(), m.getId());
                }

                // get bills
                ServerResponse.BillsResponse billsResponse = client.getBills(customCertManager, project);
                List<DBBill> remoteBills;
                List<Long> remoteAllBillIds = new ArrayList<>();
                long serverSyncTimestamp = project.getLastSyncedTimestamp();
                // IHATEMONEY => we get all bills
                remoteBills = billsResponse.getBillsIHM(project.getId(), memberRemoteIdToId);
                serverSyncTimestamp = System.currentTimeMillis() / 1000;

                applyRemoteBills(remoteBills, remoteAllBillIds, remoteMembersByRemoteId, serverSyncTimestamp);
                return true;
            } catch (ServerResponse.NotModifiedException e) {
                Log.d(TAG, "No changes, nothing to do.");
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Exception", e);
                exceptions.add(e);
                return false;
            } catch (JSONException e) {
                Log.e(TAG, "Exception", e);
                exceptions.add(e);
                return false;
            }
        }

        private void applyRemoteBills(List<DBBill> remoteBills, List<Long> remoteAllBillIds,
                                      Map<Long, DBMember> remoteMembersByRemoteId, long serverSyncTimestamp) {
            // All network requests are complete. Commit reconciliation as one disk transaction,
            // rather than fsyncing once for every expense and participant row.
            android.database.sqlite.SQLiteDatabase database = dbHelper.getWritableDatabase();
            database.beginTransaction();
            try {
                Map<Long, DBBill> remoteBillsByRemoteId = new HashMap<>();
                for (DBBill remoteBill : remoteBills) {
                    remoteBillsByRemoteId.put(remoteBill.getRemoteId(), remoteBill);
                }
                List<DBBill> localBills = dbHelper.getBillsOfProject(project.getId());
                Map<Long, DBBill> localBillsByRemoteId = new HashMap<>();
                for (DBBill localBill : localBills) {
                    localBillsByRemoteId.put(localBill.getRemoteId(), localBill);
                }

                // add, update or delete DB bills
                for (DBBill remoteBill : remoteBills) {
                    // add if local does not exist
                    if (!localBillsByRemoteId.containsKey(remoteBill.getRemoteId())) {
                        dbHelper.addBill(remoteBill);
                        nbPulledNewBills++;
                        newBillsDialogText.append("+ ").append(remoteBill.getWhat()).append('\n');
                    } else {
                        // update bill if necessary
                        // and billOwers if necessary
                        DBBill localBill = localBillsByRemoteId.get(remoteBill.getRemoteId());
                        // An edit made while the download was in flight still needs uploading.
                        if (localBill.getState() != DBBill.STATE_OK) {
                            continue;
                        }
                        // Backfill ordering metadata even when the expense itself is unchanged.
                        // Missing server metadata must also replace a provisional local date,
                        // so older servers consistently fall back to remote IDs for all bills.
                        if (!remoteBill.getCreationDate().equals(localBill.getCreationDate())) {
                            dbHelper.updateBillCreationDate(localBill.getId(), remoteBill.getCreationDate());
                        }
                        if (hasChanged(localBill, remoteBill)) {
                            dbHelper.updateBill(
                                    localBill.getId(), null, remoteBill.getPayerId(),
                                    remoteBill.getAmount(), remoteBill.getTimestamp(),
                                    remoteBill.getWhat(), DBBill.STATE_OK, remoteBill.getRepeat(),
                                    remoteBill.getPaymentMode(), remoteBill.getPaymentModeRemoteId(),
                                    remoteBill.getCategoryRemoteId(), remoteBill.getComment()
                            );
                            nbPulledUpdatedBills++;
                            updatedBillsDialogText.append("✏ ").append(remoteBill.getWhat()).append('\n');
                        }
                        //////// billowers
                        Map<Long, DBBillOwer> localBillOwersByIds = new HashMap<>();
                        for (DBBillOwer bo : localBill.getBillOwers()) {
                            localBillOwersByIds.put(bo.getMemberId(), bo);
                        }
                        Map<Long, DBBillOwer> remoteBillOwersByIds = new HashMap<>();
                        for (DBBillOwer bo : remoteBill.getBillOwers()) {
                            remoteBillOwersByIds.put(bo.getMemberId(), bo);
                        }
                        // add remote which are not here
                        for (DBBillOwer rbo : remoteBill.getBillOwers()) {
                            if (!localBillOwersByIds.containsKey(rbo.getMemberId())) {
                                dbHelper.addBillower(localBill.getId(), rbo.getMemberId());
                            }
                        }
                        // delete local which are not there remotely
                        for (DBBillOwer lbo : localBill.getBillOwers()) {
                            if (!remoteBillOwersByIds.containsKey(lbo.getMemberId())) {
                                dbHelper.deleteBillOwer(lbo.getId());
                            }
                        }
                    }
                }

                // delete local bill
                for (DBBill localBill : localBills) {
                    // if local bill does not exist remotely
                    if (localBill.getState() == DBBill.STATE_OK
                            && !remoteBillsByRemoteId.containsKey(localBill.getRemoteId())) {
                        dbHelper.deleteBill(localBill.getId());
                        nbPulledDeletedBills++;
                        deletedBillsDialogText.append("\uD83D\uDDD1 ").append(localBill.getWhat()).append('\n');
                    }
                }

                // delete local members
                // do this at the end to check if member that are not there remotely
                // don't have any bill anymore
                List<DBMember> localMembers = dbHelper.getMembersOfProject(project.getId(), null);
                for (DBMember localMember : localMembers) {
                    // if local member does not exist remotely
                    // we could trust the server, member should not be involved in anything anymore
                    if (!remoteMembersByRemoteId.containsKey(localMember.getRemoteId())) {
                        // but we check if there is any bill/billower related to the member
                        if (dbHelper.getBillsOfMember(localMember.getId()).size() == 0
                                && dbHelper.getBillowersOfMember(localMember.getId()).size() == 0) {
                            dbHelper.deleteMember(localMember.getId());
                            Log.d(TAG, "Delete local member : " + localMember);
                        } else {
                            Log.d(TAG,
                                    "WARNING local member : " + localMember.getName() + " does not exist remotely but is " +
                                            "still involved in some bills");
                        }
                    }
                }

                // finally update local project last sync timestamp
                //serverSyncTimestamp
                dbHelper.updateProject(
                        project.getId(), null, null,
                        null, null, serverSyncTimestamp,
                        null, null, null,
                        null
                );
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
            Log.i(TAG, "Pulled expenses: added=" + nbPulledNewBills + ", updated="
                    + nbPulledUpdatedBills + ", deleted=" + nbPulledDeletedBills);
        }

        /**
         * Pull a Spliit group read-only. Spliit has no auth and no push, so the
         * local cache is reconciled against the remote group and never uploaded.
         */
        private boolean pullSpliitGroup() {
            Log.d(TAG, "pullSpliitGroup(" + project + ")");
            try {
                SpliitClient spliitClient = new SpliitClient(project.getServerUrl(), project.getRemoteId());
                SpliitClient.SpliitGroup group = spliitClient.getGroup(customCertManager);

                // Keep the project name/currency in sync with the group.
                if (group.name != null && !group.name.isEmpty() && !group.name.equals(project.getName())) {
                    project.setName(group.name);
                    dbHelper.updateProject(
                            project.getId(), group.name, null, null, null, null, null, null, null, null
                    );
                }
                if (group.currency != null && !group.currency.isEmpty()
                        && !group.currency.equals(project.getCurrencyName())) {
                    project.setCurrencyName(group.currency);
                    dbHelper.updateProject(
                            project.getId(), null, null, null, null, null, group.currency, null, null, null
                    );
                }

                // The app formats amounts with a single currency; keep one local row.
                if (group.currency != null && !group.currency.isEmpty()) {
                    List<DBCurrency> localCurrencies = dbHelper.getCurrencies(project.getId());
                    if (localCurrencies.isEmpty()) {
                        dbHelper.addCurrency(new DBCurrency(
                                0, 0, project.getId(), group.currency, 1.0, DBBill.STATE_OK));
                    } else {
                        DBCurrency mainCurrency = localCurrencies.get(0);
                        if (!group.currency.equals(mainCurrency.getName())) {
                            dbHelper.updateCurrency(mainCurrency.getId(), group.currency, 1.0);
                        }
                    }
                }

                // Reconcile members by their string remote id.
                Map<String, DBMember> remoteMembersByRemoteId = new HashMap<>();
                for (SpliitClient.SpliitParticipant participant : group.participants) {
                    DBMember localMember = dbHelper.getMemberByRemoteIdStr(participant.id, project.getId());
                    if (localMember == null) {
                        DBMember newMember = new DBMember(
                                0, 0, project.getId(), participant.name,
                                true, 1, DBBill.STATE_OK, null, null, null, null, null
                        );
                        newMember.setRemoteIdStr(participant.id);
                        long mid = dbHelper.addMember(newMember);
                        remoteMembersByRemoteId.put(participant.id, dbHelper.getMember(mid));
                    } else {
                        if (participant.name != null && !participant.name.equals(localMember.getName())) {
                            dbHelper.updateMember(
                                    localMember.getId(), participant.name, null, null, null,
                                    null, null, null, null, null, null, null
                            );
                        }
                        remoteMembersByRemoteId.put(participant.id, localMember);
                    }
                }

                // Delete local members that are no longer in the group and unused.
                for (DBMember localMember : dbHelper.getMembersOfProject(project.getId(), null)) {
                    if (localMember.getRemoteIdStr() == null) {
                        continue;
                    }
                    if (!remoteMembersByRemoteId.containsKey(localMember.getRemoteIdStr())) {
                        if (dbHelper.getBillsOfMember(localMember.getId()).size() == 0
                                && dbHelper.getBillowersOfMember(localMember.getId()).size() == 0) {
                            dbHelper.deleteMember(localMember.getId());
                        }
                    }
                }

                // Reconcile expenses by their string remote id.
                Map<String, DBBill> localBillsByRemoteId = new HashMap<>();
                for (DBBill localBill : dbHelper.getBillsOfProject(project.getId())) {
                    if (localBill.getRemoteIdStr() != null) {
                        localBillsByRemoteId.put(localBill.getRemoteIdStr(), localBill);
                    }
                }
                Map<String, SpliitClient.SpliitExpense> remoteBillsByRemoteId = new HashMap<>();
                for (SpliitClient.SpliitExpense expense : group.expenses) {
                    remoteBillsByRemoteId.put(expense.id, expense);
                    DBBill localBill = localBillsByRemoteId.get(expense.id);
                    if (localBill == null) {
                        addSpliitBill(expense, remoteMembersByRemoteId);
                    } else {
                        updateSpliitBill(localBill, expense, remoteMembersByRemoteId);
                    }
                }

                // Delete local expenses that no longer exist remotely.
                for (DBBill localBill : localBillsByRemoteId.values()) {
                    if (!remoteBillsByRemoteId.containsKey(localBill.getRemoteIdStr())) {
                        dbHelper.deleteBill(localBill.getId());
                        nbPulledDeletedBills++;
                        deletedBillsDialogText.append("\uD83D\uDDD1 ").append(localBill.getWhat()).append('\n');
                    }
                }

                // Update the last sync timestamp.
                dbHelper.updateProject(
                        project.getId(), null, null, null, null,
                        System.currentTimeMillis() / 1000,
                        null, null, null, null
                );
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Exception", e);
                exceptions.add(e);
                return false;
            } catch (JSONException e) {
                Log.e(TAG, "Exception", e);
                exceptions.add(e);
                return false;
            }
        }

        private void addSpliitBill(SpliitClient.SpliitExpense expense,
                                   Map<String, DBMember> remoteMembersByRemoteId) {
            DBBill newBill = new DBBill(
                    0, 0, project.getId(), 0, expense.amount / 100.0,
                    expense.expenseDate, expense.title, DBBill.STATE_OK,
                    DBBill.NON_REPEATED, DBBill.PAYMODE_NONE, DBBill.CATEGORY_NONE,
                    expense.notes, DBBill.PAYMODE_ID_NONE
            );
            newBill.setRemoteIdStr(expense.id);
            newBill.setCreationDate(expense.createdAt);

            DBMember payer = remoteMembersByRemoteId.get(expense.paidBy);
            if (payer != null) {
                newBill.setPayerId(payer.getId());
            }
            for (String owerId : expense.paidFor) {
                DBMember ower = remoteMembersByRemoteId.get(owerId);
                if (ower != null) {
                    newBill.getBillOwers().add(new DBBillOwer(0, 0, ower.getId()));
                }
            }
            dbHelper.addBill(newBill);
            nbPulledNewBills++;
            newBillsDialogText.append("+ ").append(expense.title).append('\n');
        }

        private void updateSpliitBill(DBBill localBill, SpliitClient.SpliitExpense expense,
                                      Map<String, DBMember> remoteMembersByRemoteId) {
            DBMember payer = remoteMembersByRemoteId.get(expense.paidBy);
            long payerId = payer != null ? payer.getId() : localBill.getPayerId();
            double amount = expense.amount / 100.0;
            boolean changed = localBill.getAmount() != amount
                    || localBill.getTimestamp() != expense.expenseDate
                    || !expense.title.equals(localBill.getWhat())
                    || !expense.notes.equals(localBill.getComment())
                    || localBill.getPayerId() != payerId;
            if (changed) {
                dbHelper.updateBill(
                        localBill.getId(), null, null, payerId, amount,
                        expense.expenseDate, expense.title, DBBill.STATE_OK,
                        localBill.getRepeat(), localBill.getPaymentMode(),
                        localBill.getPaymentModeRemoteId(), localBill.getCategoryRemoteId(),
                        expense.notes
                );
                nbPulledUpdatedBills++;
                updatedBillsDialogText.append("✏ ").append(expense.title).append('\n');
            }

            // Reconcile the owers.
            Map<Long, DBBillOwer> localBillOwersByMemberId = new HashMap<>();
            for (DBBillOwer ower : localBill.getBillOwers()) {
                localBillOwersByMemberId.put(ower.getMemberId(), ower);
            }
            java.util.Set<Long> remoteOwerIds = new java.util.HashSet<>();
            for (String owerId : expense.paidFor) {
                DBMember ower = remoteMembersByRemoteId.get(owerId);
                if (ower != null) {
                    remoteOwerIds.add(ower.getId());
                    if (!localBillOwersByMemberId.containsKey(ower.getId())) {
                        dbHelper.addBillower(localBill.getId(), ower.getId());
                    }
                }
            }
            for (DBBillOwer ower : localBill.getBillOwers()) {
                if (!remoteOwerIds.contains(ower.getMemberId())) {
                    dbHelper.deleteBillOwer(ower.getId());
                }
            }
        }

        @Override
        protected void onPostExecute(Boolean status) {
            super.onPostExecute(status);
            if (!status) {
                String errorString = "";
                for (String errorMessage : errorMessages) {
                    errorString += errorMessage + "\n";
                }
                errorString += "\n";
                for (Throwable e : exceptions) {
                    JSONObject obj = SupportUtil.getJsonObject(e.getMessage());
                    if (obj != null && obj.has("message")) {
                        try {
                            errorString += obj.getString("message") + "\n";
                        } catch (JSONException jsonEx) {
                        }
                    }
                }
                // broadcast the error
                // if the bills list is not visible, no toast
                Intent intent = new Intent(BROADCAST_PROJECT_SYNC_FAILED);
                intent.putExtra(BillsListViewActivity.BROADCAST_ERROR_MESSAGE, errorString);
                intent.putExtra(BillsListViewActivity.BROADCAST_PROJECT_ID, project.getId());
                appContext.sendBroadcast(intent);
            } else {
                Intent intent = new Intent(BROADCAST_PROJECT_SYNCED);
                intent.putExtra(BillsListViewActivity.BROADCAST_EXTRA_PARAM, project.getName());
                appContext.sendBroadcast(intent);
                // NOTIFICATION
                boolean notifyNew = preferences.getBoolean(appContext.getString(R.string.pref_key_notify_new), true);
                boolean notifyUpdated = preferences.getBoolean(appContext.getString(R.string.pref_key_notify_new), true);
                boolean notifyDeleted = preferences.getBoolean(appContext.getString(R.string.pref_key_notify_new), true);
                if (!BillsListViewActivity.isActivityVisible()) {
                    String dialogContent = "";
                    String notificationContent = "";
                    if (notifyNew && nbPulledNewBills > 0) {
                        dialogContent += newBillsDialogText + "\n";
                        notificationContent += "+" + nbPulledNewBills + "  ";
                    }
                    if (notifyUpdated && nbPulledUpdatedBills > 0) {
                        dialogContent += updatedBillsDialogText + "\n";
                        notificationContent += "✏" + nbPulledUpdatedBills + "  ";
                    }
                    if (notifyDeleted && nbPulledDeletedBills > 0) {
                        dialogContent += deletedBillsDialogText;
                        notificationContent += "\uD83D\uDDD1" + nbPulledDeletedBills;
                    }
                    if (!notificationContent.equals("")) {
                        notificationContent = notificationContent.replaceAll(" \\| $", "");
                        notificationContent = appContext.getString(R.string.project_activity_notification, project.getName()) + ": " + notificationContent;
                        notifyProjectEvent(dialogContent, notificationContent, project.getId());
                    }
                }
            }
            syncActive = false;
            // notify callbacks
            for (ICallback callback : callbacks) {
                callback.onFinish();
            }
            // start next sync if there are ids in the schedule
            if (projectIdsToSync.size() > 0) {
                long pid = projectIdsToSync.remove(projectIdsToSync.size() - 1);
                scheduleSync(false, pid);
            }
        }
    }

    public static void createNotificationChannels(Context context, MoneyBusterSQLiteOpenHelper db) {
        // main channel
        SupportUtil.createNotificationChannel(MAIN_CHANNEL_ID, context.getString(R.string.permanent_notification_title), true, context);

        // one channel per project
        List<DBProject> projs = db.getProjects();
        long channelId;
        for (DBProject p : projs) {
            if (!p.isLocal()) {
                channelId = MAIN_CHANNEL_ID + p.getId();
                SupportUtil.createNotificationChannel(channelId, p.getRemoteId(), false, context);
            }
        }
    }

    public void notifyProjectEvent(String dialogContent, String notificationContent, long projectId) {
        // intent of notification
        Intent ptIntent = new Intent(appContext.getApplicationContext(), BillsListViewActivity.class);
        ptIntent.putExtra(BillsListViewActivity.PARAM_DIALOG_CONTENT, dialogContent);
        ptIntent.putExtra(BillsListViewActivity.PARAM_PROJECT_TO_SELECT, projectId);

        String chanId = String.valueOf(MAIN_CHANNEL_ID + projectId);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, chanId)
                .setSmallIcon(R.drawable.ic_dollar_grey_24dp)
                .setContentTitle(appContext.getString(R.string.app_name))
                .setContentText(notificationContent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                // Set the intent that will fire when the user taps the notification
                .setContentIntent(
                        PendingIntent.getActivity(
                                appContext, 1, ptIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT
                        )
                )
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(appContext);

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(NOTIFICATION_ID, builder.build());
        NOTIFICATION_ID++;
    }

    private VersatileProjectSyncClient createVersatileProjectSyncClient() {
        // IHateMoney requests use credentials and URLs from the project itself.
        return new VersatileProjectSyncClient("", "", "", appContext);
    }

    public boolean editRemoteProject(long projId, @Nullable String newName, @Nullable String newEmail,
                                     @Nullable String newPassword, @Nullable String newMainCurrencyName, ICallback callback) {
        updateNetworkStatus();
        if (isSyncPossible()) {
            EditRemoteProjectTask editRemoteProjectTask = new EditRemoteProjectTask(projId, newName, newEmail, newPassword, newMainCurrencyName, callback);
            editRemoteProjectTask.execute();
            return true;
        }
        return false;
    }

    /**
     * task to ask server to create public share with name restriction on device
     * or just get the share token if it already exists
     */
    private class EditRemoteProjectTask extends AsyncTask<Void, Void, Boolean> {
        private VersatileProjectSyncClient client;
        private final String newName;
        private final String newEmail;
        private final String newPassword;
        private final String newMainCurrencyName;
        private final DBProject project;
        private final ICallback callback;
        private final List<Throwable> exceptions = new ArrayList<>();
        private final List<String> errorMessages = new ArrayList<>();

        public EditRemoteProjectTask(long projId, @Nullable String newName, @Nullable String newEmail,
                                     @Nullable String newPassword, @Nullable String newMainCurrencyName, ICallback callback) {
            this.project = dbHelper.getProject(projId);
            this.newName = newName;
            this.newEmail = newEmail;
            this.newPassword = newPassword;
            this.newMainCurrencyName = newMainCurrencyName;
            this.callback = callback;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            client = createVersatileProjectSyncClient();
            if (BillsListViewActivity.DEBUG) {
                Log.i(getClass().getSimpleName(), "STARTING edit remote project");
            }
            try {
                ServerResponse.EditRemoteProjectResponse response = client.editRemoteProject(
                    customCertManager, project, newName, newEmail, newPassword, newMainCurrencyName
                );
                if (BillsListViewActivity.DEBUG) {
                    Log.i(getClass().getSimpleName(), "RESPONSE edit remote project : " + response.getStringContent());
                }
            } catch (IOException e) {
                if (BillsListViewActivity.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                return false;
            } catch (JSONException e) {
                if (BillsListViewActivity.DEBUG) {
                    Log.d(getClass().getSimpleName(), "JSONException", e);
                }
            }
            if (BillsListViewActivity.DEBUG) {
                Log.i(getClass().getSimpleName(), "FINISHED edit remote project");
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean status) {
            super.onPostExecute(status);
            String errorString = "";
            if (!status) {
                for (String errorMessage : errorMessages) {
                    errorString += errorMessage + "\n";
                }
                errorString += "\n";
                for (Throwable e : exceptions) {
                    errorString += e.getClass().getName() + ": " + e.getMessage();
                }
            } else {
                dbHelper.updateProject(
                        project.getId(), newName, newEmail, newPassword,
                        null, null,
                        null, null, null,
                        null
                );
            }
            callback.onFinish(newName, errorString);
        }
    }

    public boolean deleteRemoteProject(long projId, ICallback callback) {
        updateNetworkStatus();
        if (isSyncPossible()) {
            DeleteRemoteProjectTask deleteRemoteProjectTask = new DeleteRemoteProjectTask(projId, callback);
            deleteRemoteProjectTask.execute();
            return true;
        }
        return false;
    }

    /**
     * task to ask server to create public share with name restriction on device
     * or just get the share token if it already exists
     */
    private class DeleteRemoteProjectTask extends AsyncTask<Void, Void, Boolean> {
        private VersatileProjectSyncClient client;
        private final DBProject project;
        private final ICallback callback;
        private final List<Throwable> exceptions = new ArrayList<>();
        private final List<String> errorMessages = new ArrayList<>();

        public DeleteRemoteProjectTask(long projId, ICallback callback) {
            this.project = dbHelper.getProject(projId);
            this.callback = callback;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            client = createVersatileProjectSyncClient();
            if (BillsListViewActivity.DEBUG) {
                Log.i(getClass().getSimpleName(), "STARTING delete remote project");
            }
            try {
                ServerResponse.DeleteRemoteProjectResponse response = client.deleteRemoteProject(customCertManager, project);
                if (BillsListViewActivity.DEBUG) {
                    Log.i(getClass().getSimpleName(), "RESPONSE delete remote project : " + response.getStringContent());
                }
            } catch (IOException e) {
                if (BillsListViewActivity.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                return false;
            } catch (JSONException e) {
                if (BillsListViewActivity.DEBUG) {
                    Log.d(getClass().getSimpleName(), "JSONException", e);
                }
            }
            if (BillsListViewActivity.DEBUG) {
                Log.i(getClass().getSimpleName(), "FINISHED delete device");
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean status) {
            super.onPostExecute(status);
            String errorString = "";
            if (!status) {
                for (String errorMessage : errorMessages) {
                    errorString += errorMessage + "\n";
                }
                errorString += "\n";
                for (Throwable e : exceptions) {
                    errorString += e.getClass().getName() + ": " + e.getMessage();
                }
            } else {
                dbHelper.deleteProject(project.getId());
            }
            callback.onFinish(String.valueOf(project.getId()), errorString);
        }
    }

    public boolean createRemoteProject(String remoteId, String name, String email, String password, String ihmUrl, ProjectType projectType, IProjectCreationCallback callback) {
        if (isSyncPossible()) {
            DBProject proj = new DBProject(
                    0, remoteId,
                    password,
                    name, ihmUrl, email,
                    null, projectType, 0L, null,
                    false, DBProject.ACCESS_LEVEL_UNKNOWN, null
            );
            CreateRemoteProjectTask createRemoteProjectTask = new CreateRemoteProjectTask(proj, callback);
            createRemoteProjectTask.execute();
            return true;
        }
        return false;
    }

    /**
     * task to ask server to create public share with name restriction on device
     * or just get the share token if it already exists
     */
    private class CreateRemoteProjectTask extends AsyncTask<Void, Void, Boolean> {
        private VersatileProjectSyncClient client;
        private final DBProject project;
        private final IProjectCreationCallback callback;
        private final List<Throwable> exceptions = new ArrayList<>();
        private final List<String> errorMessages = new ArrayList<>();
        private boolean usePrivateApi = false;

        public CreateRemoteProjectTask(DBProject project, IProjectCreationCallback callback) {
            this.project = project;
            this.callback = callback;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            client = createVersatileProjectSyncClient();
            if (BillsListViewActivity.DEBUG) {
                Log.i(getClass().getSimpleName(), "STARTING create remote project");
            }
            try {
                ServerResponse.CreateRemoteProjectResponse response = client.createAnonymousRemoteProject(customCertManager, project);
                if (BillsListViewActivity.DEBUG) {
                    Log.i(getClass().getSimpleName(), "RESPONSE create remote project : " + response.getStringContent());
                }
            } catch (IOException e) {
                if (BillsListViewActivity.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                return false;
            } catch (JSONException e) {
                if (BillsListViewActivity.DEBUG) {
                    Log.d(getClass().getSimpleName(), "JSONException", e);
                }
            }
            if (BillsListViewActivity.DEBUG) {
                Log.i(getClass().getSimpleName(), "FINISHED create remote project");
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean status) {
            super.onPostExecute(status);
            String errorString = "";
            if (!status) {
                for (String errorMessage : errorMessages) {
                    errorString += errorMessage + "\n";
                }
                errorString += "\n";
                for (Throwable e : exceptions) {
                    errorString += e.getClass().getName() + ": " + e.getMessage();
                }
            }
            callback.onFinish(project.getRemoteId(), errorString, usePrivateApi);
        }
    }

    private boolean hasChanged(DBBill localBill, DBBill remoteBill) {
        if (localBill.getPayerId() == remoteBill.getPayerId() &&
            localBill.getAmount() == remoteBill.getAmount() &&
            localBill.getTimestamp() == remoteBill.getTimestamp() &&
            localBill.getWhat().equals(remoteBill.getWhat()) &&
            localBill.getComment().equals(remoteBill.getComment()) &&
            localBill.getPaymentMode().equals(remoteBill.getPaymentMode()) &&
            localBill.getPaymentModeRemoteId() == remoteBill.getPaymentModeRemoteId() &&
            localBill.getCategoryRemoteId() == remoteBill.getCategoryRemoteId()
        ) {
            String localRepeat = localBill.getRepeat() == null ? DBBill.NON_REPEATED : localBill.getRepeat();
            String remoteRepeat = remoteBill.getRepeat() == null ? DBBill.NON_REPEATED : remoteBill.getRepeat();

            return !localRepeat.equals(remoteRepeat);
        } else {
            return true;
        }
    }

    // member avatars are not supported for IHateMoney projects
    public void updateMemberAvatar(long memberId) {
        // IHateMoney has no account avatars.
    }

    public boolean canConnectToRemoteProject(DBProject project) {
        updateNetworkStatus();
        if (!isSyncPossible()) {
            return false;
        }
        if (project.isReadOnly()) {
            try {
                SpliitClient client = new SpliitClient(project.getServerUrl(), project.getRemoteId());
                SpliitClient.SpliitGroup group = client.getGroup(customCertManager);
                return group.name != null;
            } catch (Exception e) {
                Log.e(TAG, "Failed to get Spliit group: " + e);
                return false;
            }
        }
        VersatileProjectSyncClient client = createVersatileProjectSyncClient();
        try {
            ServerResponse.ProjectResponse projResponse = client.getProject(customCertManager, project, 0, null);
            projResponse.getName();
            projResponse.getEmail();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get project info: " + e);
            return false;
        }
        return true;
    }

    // get remote project info
    public boolean getRemoteProjectInfo(DBProject project, ICallback callback) {
        if (isSyncPossible()) {
            if (project.isReadOnly()) {
                GetRemoteSpliitProjectInfoTask getRemoteSpliitProjectInfoTask =
                        new GetRemoteSpliitProjectInfoTask(project, callback);
                getRemoteSpliitProjectInfoTask.execute();
            } else {
                GetRemoteProjectInfoTask getRemoteProjectInfoTask = new GetRemoteProjectInfoTask(project, callback);
                getRemoteProjectInfoTask.execute();
            }
            return true;
        }
        return false;
    }

    /**
     * task to ask server to create public share with name restriction on device
     * or just get the share token if it already exists
     */
    private class GetRemoteProjectInfoTask extends AsyncTask<Void, Void, Boolean> {
        private VersatileProjectSyncClient client;
        private final DBProject project;
        private final ICallback callback;
        private final List<Throwable> exceptions = new ArrayList<>();
        private final List<String> errorMessages = new ArrayList<>();

        public GetRemoteProjectInfoTask(DBProject project, ICallback callback) {
            this.project = project;
            this.callback = callback;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            client = createVersatileProjectSyncClient();
            if (BillsListViewActivity.DEBUG) {
                Log.i(getClass().getSimpleName(), "STARTING create remote project");
            }
            try {
                ServerResponse.ProjectResponse projResponse = client.getProject(customCertManager, project, 0, null);
                String name = projResponse.getName();
                String email = projResponse.getEmail();
                Log.e(TAG, "Project info: " + name + " and " + email);
            } catch (IOException e) {
                if (BillsListViewActivity.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception2", e);
                }
                exceptions.add(e);
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Failed to get project info: " + e);
                exceptions.add(e);
                return false;
            }
            if (BillsListViewActivity.DEBUG) {
                Log.i(getClass().getSimpleName(), "FINISHED create remote project");
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean status) {
            super.onPostExecute(status);
            String errorString = "";
            if (!status) {
                for (String errorMessage : errorMessages) {
                    errorString += errorMessage + "\n";
                }
                errorString += "\n";
                for (Throwable e : exceptions) {
                    errorString += e.getClass().getName() + ": " + e.getMessage();
                }
            }
            callback.onFinish(null, errorString);
        }
    }

    /**
     * Fetch a Spliit group's metadata and fill the project name/currency before
     * the project is added to the local database.
     */
    private class GetRemoteSpliitProjectInfoTask extends AsyncTask<Void, Void, Boolean> {
        private final DBProject project;
        private final ICallback callback;
        private final List<Throwable> exceptions = new ArrayList<>();
        private String name;

        public GetRemoteSpliitProjectInfoTask(DBProject project, ICallback callback) {
            this.project = project;
            this.callback = callback;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                SpliitClient client = new SpliitClient(project.getServerUrl(), project.getRemoteId());
                SpliitClient.SpliitGroup group = client.getGroup(customCertManager);
                project.setName(group.name);
                project.setCurrencyName(group.currency);
                name = group.name;
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to get Spliit group info", e);
                exceptions.add(e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean status) {
            super.onPostExecute(status);
            if (status) {
                callback.onFinish(name, "");
            } else {
                String errorString = "";
                for (Throwable e : exceptions) {
                    errorString += e.getClass().getName() + ": " + e.getMessage() + "\n";
                }
                callback.onFinish(null, errorString);
            }
        }
    }
}
