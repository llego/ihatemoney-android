package net.llego.ihatemoney.android.fragment;

import static android.app.Activity.RESULT_OK;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.android.activity.QrCodeScannerActivity;
import net.llego.ihatemoney.databinding.ActivityNewProjectBinding;
import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.model.DBCategory;
import net.llego.ihatemoney.model.DBCurrency;
import net.llego.ihatemoney.model.DBMember;
import net.llego.ihatemoney.model.DBPaymentMode;
import net.llego.ihatemoney.model.DBProject;
import net.llego.ihatemoney.model.ProjectType;
import net.llego.ihatemoney.persistence.MoneyBusterSQLiteOpenHelper;
import net.llego.ihatemoney.theme.ThemeUtils;
import net.llego.ihatemoney.theme.ThemedFragment;
import net.llego.ihatemoney.theme.ThemedMaterialAlertDialogBuilder;
import net.llego.ihatemoney.theme.ThemedProgressDialogBuilder;
import net.llego.ihatemoney.util.ICallback;
import net.llego.ihatemoney.util.EdgeToEdge;
import net.llego.ihatemoney.util.IProjectCreationCallback;
import net.llego.ihatemoney.util.SupportUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NewProjectFragment extends ThemedFragment {

    private static final String TAG = NewProjectFragment.class.getSimpleName();

    public static final String PARAM_DEFAULT_IHM_URL = "defaultIhmUrl";
    public static final String PARAM_DEFAULT_NC_URL = "defaultNcUrl";
    public static final String PARAM_DEFAULT_PROJECT_ID = "defaultProjectId";
    public static final String PARAM_DEFAULT_PROJECT_PASSWORD = "defaultProjectPassword";
    public static final String PARAM_DEFAULT_PROJECT_TYPE = "defaultProjectType";
    public static final String PARAM_IS_IMPORT = "isImport";

    private ActivityNewProjectBinding binding;

    public interface NewProjectFragmentListener {
        void close(long pid, boolean justAdded);
    }

    @Nullable
    protected MoneyBusterSQLiteOpenHelper db;
    protected NewProjectFragmentListener listener;

    private Handler handler;

    protected EditText newProjectId;
    protected EditText newProjectUrl;
    protected EditText newProjectPassword;
    protected MaterialButton whatTodoJoin;
    protected MaterialButton whatTodoCreate;
    protected MaterialButton whereLocal;
    protected MaterialButton whereIhm;
    protected MaterialButton whereSpliit;
    protected EditText newProjectEmail;
    protected EditText newProjectName;

    protected ImageView whereIcon;

    protected LinearLayout newProjectIdLayout;
    protected LinearLayout newProjectUrlLayout;
    protected TextInputLayout newProjectIdInputLayout;
    protected TextInputLayout newProjectUrlInputLayout;
    protected TextInputLayout newProjectPasswordInputLayout;
    protected TextInputLayout newProjectNameInputLayout;
    protected TextInputLayout newProjectEmailInputLayout;
    protected LinearLayout newProjectPasswordLayout;
    protected LinearLayout newProjectNameLayout;
    protected LinearLayout newProjectEmailLayout;

    protected Button scanButton;
    protected Button importButton;

    protected MaterialButton fabOk;

    protected String defaultIhmUrl;
    protected String defaultNcUrl;

    private AlertDialog progress = null;

    /** Project object whose name/currency are filled by the remote info fetch. */
    private DBProject pendingRemoteProject;

    public static NewProjectFragment newInstance(String defaultIhmUrl, String defaultNCUrl,
                                                 @Nullable String defaultProjectId,
                                                 @Nullable String defaultProjectPassword,
                                                 ProjectType defaultProjectType,
                                                 Boolean isImport) {
        NewProjectFragment f = new NewProjectFragment();
        Bundle b = new Bundle();
        b.putString(PARAM_DEFAULT_IHM_URL, defaultIhmUrl);
        b.putString(PARAM_DEFAULT_NC_URL, defaultNCUrl);
        b.putString(PARAM_DEFAULT_PROJECT_ID, defaultProjectId);
        b.putString(PARAM_DEFAULT_PROJECT_PASSWORD, defaultProjectPassword);
        b.putString(PARAM_DEFAULT_PROJECT_TYPE, defaultProjectType.getId());
        b.putBoolean(PARAM_IS_IMPORT, isImport);
        f.setArguments(b);
        return f;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = ActivityNewProjectBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        EdgeToEdge.apply(requireActivity(), view);

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        FragmentActivity activity = getActivity();
        if (activity != null) {
            ((AppCompatActivity) activity).setSupportActionBar(toolbar);
        }
        whatTodoJoin = view.findViewById(R.id.whatTodoJoin);
        whatTodoCreate = view.findViewById(R.id.whatTodoCreate);
        whereLocal = view.findViewById(R.id.whereLocal);
        whereIhm = view.findViewById(R.id.whereIhm);
        whereSpliit = view.findViewById(R.id.whereSpliit);
        whereIcon = view.findViewById(R.id.whereIcon);

        newProjectId = view.findViewById(R.id.editProjectId);
        newProjectUrl = view.findViewById(R.id.editProjectUrl);
        newProjectPassword = view.findViewById(R.id.editProjectPassword);
        newProjectEmail = view.findViewById(R.id.editProjectEmail);
        newProjectName = view.findViewById(R.id.editProjectName);

        newProjectIdLayout = view.findViewById(R.id.editProjectIdLayout);
        newProjectUrlLayout = view.findViewById(R.id.editProjectUrlLayout);
        newProjectIdInputLayout = view.findViewById(R.id.editProjectIdInputLayout);
        newProjectUrlInputLayout = view.findViewById(R.id.editProjectUrlInputLayout);
        newProjectPasswordInputLayout = view.findViewById(R.id.editProjectPasswordInputLayout);
        newProjectNameInputLayout = view.findViewById(R.id.editProjectNameInputLayout);
        newProjectEmailInputLayout = view.findViewById(R.id.editProjectEmailInputLayout);
        newProjectPasswordLayout = view.findViewById(R.id.editProjectPasswordLayout);
        newProjectEmailLayout = view.findViewById(R.id.editProjectEmailLayout);
        newProjectNameLayout = view.findViewById(R.id.editProjectNameLayout);

        scanButton = view.findViewById(R.id.scanButton);
        importButton = view.findViewById(R.id.importButton);

        fabOk = view.findViewById(R.id.fab_new_ok);

        whatTodoJoin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean isChecked = ((MaterialButton)view).isChecked();
                if (isChecked) {
                    whatTodoCreate.setChecked(false);
                    showHideInputFields(true);
                    showHideValidationButtons();
                } else {
                    whatTodoJoin.setChecked(true);
                }
            }
        });
        whatTodoCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean isChecked = ((MaterialButton)view).isChecked();
                if (isChecked) {
                    whatTodoJoin.setChecked(false);
                    showHideInputFields(true);
                    showHideValidationButtons();
                } else {
                    whatTodoCreate.setChecked(true);
                }
            }
        });

        whereLocal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean isChecked = ((MaterialButton)view).isChecked();
                if (isChecked) {
                    whereIhm.setChecked(false);
                    whereSpliit.setChecked(false);
                    showHideInputFields(true);
                    showHideValidationButtons();
                } else {
                    whereLocal.setChecked(true);
                }
            }
        });
        whereIhm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean isChecked = ((MaterialButton)view).isChecked();
                if (isChecked) {
                    whereLocal.setChecked(false);
                    whereSpliit.setChecked(false);
                    showHideInputFields(true);
                    showHideValidationButtons();
                } else {
                    whereIhm.setChecked(true);
                }
            }
        });
        whereSpliit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean isChecked = ((MaterialButton)view).isChecked();
                if (isChecked) {
                    whereLocal.setChecked(false);
                    whereIhm.setChecked(false);
                    showHideInputFields(true);
                    showHideValidationButtons();
                } else {
                    whereSpliit.setChecked(true);
                }
            }
        });

        fabOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onPressOk();
            }
        });

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Scan button pressed");
                Intent createIntent = new Intent(getContext(), QrCodeScannerActivity.class);
                scanQRCodeLauncher.launch(createIntent);
            }
        });

        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent()
                        .setType("*/*")
                        .setAction(Intent.ACTION_GET_CONTENT);

                importFileLauncher.launch(Intent.createChooser(intent, "Select a file"));
            }
        });

        newProjectId.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "project id change");
                showHideValidationButtons();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        newProjectUrl.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "project url change");
                showHideValidationButtons();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        newProjectName.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "project name change");
                showHideValidationButtons();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
        newProjectPassword.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "project password change");
                showHideValidationButtons();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
        newProjectEmail.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "project email change");
                showHideValidationButtons();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        return view;
    }

    @Override
    public void applyTheme(int color) {
        final var utils = ThemeUtils.of(color, requireContext());
        utils.material.colorMaterialButtonPrimaryFilled(binding.newProjectForm.fabNewOk);
        utils.material.colorTextInputLayout(binding.newProjectForm.editProjectUrlInputLayout);
        utils.material.colorTextInputLayout(binding.newProjectForm.editProjectIdInputLayout);
        utils.material.colorTextInputLayout(binding.newProjectForm.editProjectPasswordInputLayout);
        utils.material.colorTextInputLayout(binding.newProjectForm.editProjectNameInputLayout);
        utils.material.colorTextInputLayout(binding.newProjectForm.editProjectEmailInputLayout);
        utils.material.colorMaterialButtonText(binding.newProjectForm.scanButton);
        utils.material.colorMaterialButtonText(binding.newProjectForm.importButton);
    }

    private void showHideValidationButtons() {
        fabOk.setEnabled(isFormValid());
    }

    private boolean isFormValid() {
        boolean valid = true;
        boolean todoCreate = getTodoCreate();
        ProjectType type = getProjectType();

        if (type.equals(ProjectType.SPLIIT)) {
            return isSpliitFormValid();
        }

        String projectId = getRemoteId();
        String projectUrl = getUrl();
        String projectPassword = getPassword();
        String projectName = getName();
        String projectEmail = getEmail();

        boolean isIhmInvitationLink = type.equals(ProjectType.IHATEMONEY)
                && isValidUrl(projectUrl)
                && isInvitationLink(projectUrl);
        // in "join" mode, show/hide projId and password if this is an IHM invitation link
        if (!todoCreate && isIhmInvitationLink) {
            newProjectPasswordLayout.setVisibility(View.GONE);
            newProjectIdLayout.setVisibility(View.GONE);
        } else {
            newProjectPasswordLayout.setVisibility(type.equals(ProjectType.LOCAL)
                    ? View.GONE : View.VISIBLE);
            newProjectIdLayout.setVisibility(View.VISIBLE);
        }

        // always check project ID
        if (todoCreate) {
            if (projectId.equals("")) {
                valid = false;
            }
        } else {
            if (projectId.equals("") && !isIhmInvitationLink) {
                valid = false;
            }
        }

        if (!type.equals(ProjectType.LOCAL)) {
            if (
                    (projectUrl.equals("") || !isValidUrl(projectUrl))
            ) {
                valid = false;
            }
            if (todoCreate) {
                if (type.equals(ProjectType.IHATEMONEY) && projectPassword.equals("")) {
                    valid = false;
                }
            } else {
                if (projectPassword.equals("") && !isIhmInvitationLink) {
                    valid = false;
                }
            }
        }

        // create
        if (todoCreate) {
            if (!type.equals(ProjectType.LOCAL)) {
                if (projectName.equals("")) {
                    valid = false;
                }
                if (!SupportUtil.isValidEmail(projectEmail)) {
                    valid = false;
                }
            }
        } else {
            // join
            if (type.equals(ProjectType.LOCAL)) {
                valid = false;
            }
        }

        return valid;
    }

    private boolean isSpliitFormValid() {
        String projectUrl = getUrl();
        String projectId = getSpliitGroupId();
        return !projectUrl.isEmpty() && isValidUrl(projectUrl) && !projectId.isEmpty();
    }

    /**
     * Spliit group id can be entered directly or taken from a group URL
     * (e.g. https://spliit.app/groups/abc123).
     */
    private String getSpliitGroupId() {
        String id = getRemoteId();
        if (id != null && !id.isEmpty()) {
            Uri idUri = Uri.parse(id);
            if (isSpliitGroupUrl(idUri)) {
                return idUri.getLastPathSegment();
            }
            return id;
        }
        Uri uri = Uri.parse(getUrl());
        if (isSpliitGroupUrl(uri)) {
            return uri.getLastPathSegment();
        }
        return "";
    }

    private String getSpliitBaseUrl() {
        String id = getRemoteId();
        Uri idUri = Uri.parse(id);
        if (isSpliitGroupUrl(idUri)) {
            return spliitBaseUrl(idUri);
        }
        Uri uri = Uri.parse(getUrl());
        if (isSpliitGroupUrl(uri)) {
            return spliitBaseUrl(uri);
        }
        return getUrl();
    }

    /** Keep any sub-path a self-hosted Spliit instance is mounted under. */
    private String spliitBaseUrl(Uri groupUri) {
        StringBuilder base = new StringBuilder(groupUri.getScheme())
                .append("://").append(groupUri.getHost());
        if (groupUri.getPort() != -1) {
            base.append(":").append(groupUri.getPort());
        }
        List<String> segments = groupUri.getPathSegments();
        for (int i = 0; i < segments.size() - 2; i++) {
            base.append("/").append(segments.get(i));
        }
        return base.toString();
    }

    private boolean isSpliitGroupUrl(Uri uri) {
        return uri.getScheme() != null
                && (uri.getScheme().equals("https") || uri.getScheme().equals("http"))
                && uri.getPathSegments().size() >= 2
                && "groups".equals(uri.getPathSegments().get(uri.getPathSegments().size() - 2));
    }

    private void showHideInputFields(boolean setDefaultUrl) {
        boolean todoCreate = getTodoCreate();
        ProjectType type = getProjectType();

        // Spliit is join-only: hide the create toggle and force join.
        if (type.equals(ProjectType.SPLIIT)) {
            whatTodoCreate.setVisibility(View.GONE);
            whatTodoJoin.setChecked(true);
            todoCreate = false;
        } else {
            whatTodoCreate.setVisibility(View.VISIBLE);
        }

        // change 'where' selection if we want to join a project and 'where' is local
        if (!todoCreate && type.equals(ProjectType.LOCAL)) {
            whereLocal.setChecked(false);
            whereIhm.setChecked(true);
            type = getProjectType();
        }
        whereLocal.setVisibility(todoCreate ? View.VISIBLE : View.GONE);
        // Keep the where selector visible in join mode so Spliit groups can be
        // joined; Local stays hidden because local projects cannot be joined.
        binding.newProjectForm.whereLayout.setVisibility(View.VISIBLE);
        fabOk.setText(todoCreate ? R.string.todo_create_label : R.string.todo_join_label);

        newProjectUrlLayout.setVisibility(!type.equals(ProjectType.LOCAL) ? View.VISIBLE : View.GONE);
        newProjectPasswordLayout.setVisibility(!type.equals(ProjectType.LOCAL)
                && !type.equals(ProjectType.SPLIIT) ? View.VISIBLE : View.GONE);

        boolean showNameEmail = (todoCreate && !type.equals(ProjectType.LOCAL));
        newProjectNameLayout.setVisibility(showNameEmail ? View.VISIBLE : View.GONE);
        newProjectEmailLayout.setVisibility(showNameEmail ? View.VISIBLE : View.GONE);

        scanButton.setVisibility(todoCreate ? View.GONE : View.VISIBLE);

        if (type.equals(ProjectType.LOCAL)) {
            whereIcon.setImageResource(R.drawable.ic_cellphone_grey_24dp);

            importButton.setVisibility(View.VISIBLE);
        } else if (type.equals(ProjectType.IHATEMONEY)) {
            whereIcon.setImageResource(R.drawable.ic_ihm_grey_24dp);

            if (setDefaultUrl) {
                newProjectUrl.setText(defaultIhmUrl);
            }
            newProjectUrlInputLayout.setHint(getString(R.string.setting_ihm_project_url));
            importButton.setVisibility(View.GONE);
        } else if (type.equals(ProjectType.SPLIIT)) {
            whereIcon.setImageResource(R.drawable.ic_ihm_grey_24dp);

            if (setDefaultUrl) {
                newProjectUrl.setText(defaultIhmUrl);
            }
            newProjectUrlInputLayout.setHint(getString(R.string.spliit_join_url));
            importButton.setVisibility(View.GONE);
        }
    }

    private void hideKeyboard(Context context) {
        // hide keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler(Looper.getMainLooper());

        Log.d(TAG, "PROJECT on create : ");

        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            listener = (NewProjectFragmentListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.getClass() + " must implement " + NewProjectFragmentListener.class);
        }
        db = MoneyBusterSQLiteOpenHelper.getInstance(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        //listener.onProjectUpdated(project);
    }

    @Override
    public void onPause() {
        super.onPause();
        //saveBill(null);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        showHideInputFields(false);
        showHideValidationButtons();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, "PROJECT SAVE INSTANCE STATE");
        //saveBill(null);
        //outState.putSerializable(SAVEDKEY_PROJECT, project);
    }

    private final ActivityResultLauncher<Intent> scanQRCodeLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            Intent data = result.getData();
                            if (result.getResultCode() == RESULT_OK) {
                                if (data != null) {
                                    String scannedUrl = data.getStringExtra(QrCodeScannerActivity.KEY_QR_CODE);
                                    Log.d(TAG, "onActivityResult SCANNED URL : " + scannedUrl);
                                    applyMBLink(Uri.parse(scannedUrl));
                                }
                            }
                        }
                    });

    private final ActivityResultLauncher<Intent> importFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            Intent data = result.getData();
                            if (result.getResultCode() == RESULT_OK) {
                                if (data != null) {
                                    Uri selectedfile = data.getData();
                                    Log.v(TAG, "here is the selected file : " + selectedfile.toString());
                                    importFromFile(selectedfile);
                                }
                            }
                        }
                    });

    private void applyMBLink(Uri data) {
        String password;
        String url;
        String pid;
        if (data.getScheme() != null && data.getScheme().equals("spliit")) {
            // spliit://<host>[:port]/<base-path...>/<group>
            List<String> segments = data.getPathSegments();
            StringBuilder base = new StringBuilder("https://").append(data.getHost());
            if (data.getPort() != -1) {
                base.append(":").append(data.getPort());
            }
            for (int i = 0; i < segments.size() - 1; i++) {
                base.append("/").append(segments.get(i));
            }
            newProjectUrl.setText(base.toString());
            newProjectId.setText(segments.isEmpty() ? "" : data.getLastPathSegment());
            newProjectPassword.setText("");
            whereLocal.setChecked(false);
            whereIhm.setChecked(false);
            whereSpliit.setChecked(true);
            showHideInputFields(false);
            showHideValidationButtons();
            if (isFormValid()) {
                onPressOk();
            }
            return;
        }
        if (isSpliitGroupUrl(data)) {
            String groupId = data.getLastPathSegment();
            newProjectUrl.setText(spliitBaseUrl(data));
            newProjectId.setText(groupId);
            newProjectPassword.setText("");
            whereLocal.setChecked(false);
            whereIhm.setChecked(false);
            whereSpliit.setChecked(true);
            showHideInputFields(false);
            showHideValidationButtons();
            if (isFormValid()) {
                onPressOk();
            }
            return;
        }
        if (
                data.getScheme() != null
                && (data.getScheme().equals("ihatemoney") || data.getScheme().equals("ihatemoney+http"))
                && data.getPathSegments().size() >= 1
        ) {
            if (data.getPathSegments().size() >= 3
                    && "join".equals(data.getPathSegments().get(data.getPathSegments().size() - 2))) {
                newProjectPassword.setText("");
                newProjectId.setText("");
                String protocol = "https";
                if (data.getScheme().equals("ihatemoney+http")) {
                    protocol = "http";
                }
                url = protocol + "://" + data.getHost();
                if (data.getPort() != -1) {
                    url += ":" + data.getPort();
                }
                url += data.getPath();
            } else {
                if (data.getPath().endsWith("/")) {
                    password = "";
                    pid = data.getLastPathSegment();
                } else {
                    password = data.getLastPathSegment();
                    pid = data.getPathSegments().get(data.getPathSegments().size() - 2);
                }
                String protocol = "https";
                if (data.getScheme().equals("ihatemoney+http")) {
                    protocol = "http";
                }
                url = protocol + "://" + data.getHost();
                if (data.getPort() != -1) {
                    url += ":" + data.getPort();
                }
                url += data.getPath().replaceAll("/" + pid + "/" + password + "$", "");
                newProjectPassword.setText(password);
                newProjectId.setText(pid);
            }
            newProjectUrl.setText(url);
            whereLocal.setChecked(false);
            whereIhm.setChecked(true);
            showHideInputFields(false);
            showHideValidationButtons();
        } else {
            showToast(getString(R.string.import_bad_url), Toast.LENGTH_LONG);
            return;
        }

        if (isFormValid()) {
            onPressOk();
        } else {
            if (getPassword().equals("")) {
                newProjectPassword.requestFocus();
            }
        }
    }

    private void onPressOk() {
        createProject();
    }

    private void createProject() {
        ProjectType type = getProjectType();
        boolean isSpliitGroupUrl = type.equals(ProjectType.SPLIIT) && isSpliitGroupUrl(Uri.parse(getUrl()));
        boolean isIhmInvitationLink = isInvitationLink(getUrl());
        String rid = type.equals(ProjectType.SPLIIT) ? getSpliitGroupId() : getRemoteId();
        if (!isIhmInvitationLink && !isSpliitGroupUrl
                && (rid == null || rid.equals("") || rid.contains(",") || rid.contains("/"))) {
            showToast(getString(R.string.error_invalid_project_remote_id), Toast.LENGTH_LONG);
            return;
        }

        if (!ProjectType.LOCAL.equals(type)) {
            if (!isIhmInvitationLink) {
                // check values
                String url = getUrl();
                if (!isValidUrl(url)) {
                    //showToast(getString(R.string.error_invalid_url), Toast.LENGTH_LONG);
                    Log.e(TAG, "invalid URL");
                    return;
                }
                if (ProjectType.IHATEMONEY.equals(type)) {
                    String pwd = getPassword();
                    if (url != null && !url.equals("") && (pwd == null || pwd.equals(""))) {
                        //showToast(getString(R.string.error_invalid_project_password), Toast.LENGTH_LONG);
                        Log.e(TAG, "invalid password");
                        return;
                    }
                }
            }
        }

        // join or create local
        boolean todoCreate = getTodoCreate();
        if (!todoCreate || ProjectType.LOCAL.equals(type)) {
            if (ProjectType.LOCAL.equals(type)) {
                long pid = saveLocalProject(null, false);
                // if it's local, we call that creation, otherwise we can say it's been "added"
                listener.close(pid, false);
            } else {
                saveRemoteProject(null, false);
            }
        } else {
            // create remote project (we know the type is not local)
            // the callback will quit this activity
            String name = getName();
            if (name == null || name.equals("")) {
                //showToast(getString(R.string.error_invalid_project_name), Toast.LENGTH_LONG);
                Log.e(TAG, "invalid project name");
                return;
            }
            if (!SupportUtil.isValidEmail(getEmail())) {
                //showToast(getString(R.string.error_invalid_email), Toast.LENGTH_LONG);
                Log.e(TAG, "invalid email");
                return;
            }
            AlertDialog.Builder builder = new ThemedProgressDialogBuilder(getContext());
            builder.setTitle(getString(R.string.simple_loading));
            builder.setMessage(getString(R.string.creating_remote_project));
            builder.setCancelable(false); // disable dismiss by tapping outside of the dialog
            progress = builder.show();
            if (!db.getMoneyBusterServerSyncHelper().createRemoteProject(getRemoteId(), getName(),
                    getEmail(), getPassword(), getUrl(), getProjectType(), createRemoteCallBack)) {
                //showToast(getString(R.string.remote_project_operation_no_network), Toast.LENGTH_LONG);
                progress.dismiss();
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        //menu.findItem(R.id.menu_delete_remote).setVisible(false);
    }

    /**
     * Main-Menu-Handler
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onCloseProject() {
        Log.d(getClass().getSimpleName(), "onCLOSE()");
    }

    protected long saveLocalProject(@Nullable ICallback callback, boolean ignorePassword) {
        ProjectType type = getProjectType();
        String remoteId = getRemoteId();
        String name = getRemoteId();
        DBProject newProject = new DBProject(
                0, remoteId, null, name, null,
                null, null, type, 0L,
                null, false, DBProject.ACCESS_LEVEL_UNKNOWN,
                null
        );
        return addProjectToDb(newProject);
    }

    protected void saveRemoteProject(@Nullable ICallback callback, boolean ignorePassword) {
        DBProject newProject = getProjectFromFields(ignorePassword);
        pendingRemoteProject = newProject;
        Log.e(TAG, "URLLLLL " + newProject.getServerUrl());
        Log.e(TAG, "IDDDDDDDD " + newProject.getRemoteId());
        Log.e(TAG, "TOKENNNNN " + newProject.getBearerToken());
        if (!db.getMoneyBusterServerSyncHelper().getRemoteProjectInfo(newProject, getRemoteInfoCallBack)) {
            showToast(getString(R.string.error_no_network), Toast.LENGTH_LONG);
        }
    }

    private DBProject getProjectFromFields(boolean ignorePassword) {
        ProjectType type = getProjectType();
        String remoteId = getRemoteId();
        String url = getUrl();
        String email = getEmail();
        String name = getName();
        String password = "";
        String bearerToken = null;
        if (!ignorePassword) {
            password = getPassword();
        }
        if (type.equals(ProjectType.SPLIIT)) {
            // Spliit is anonymous: the group id is the capability, no password.
            remoteId = getSpliitGroupId();
            url = getSpliitBaseUrl();
            password = "";
            email = "";
            return new DBProject(
                    0, remoteId, password, name, url,
                    email, null, type, 0L,
                    null, false, DBProject.ACCESS_LEVEL_UNKNOWN,
                    null
            );
        }
        // handle IHM invitation links
        if (type.equals(ProjectType.IHATEMONEY) && isInvitationLink(url)) {
            password = "";

            Uri data = Uri.parse(url);
            bearerToken = data.getPathSegments().get(data.getPathSegments().size() - 1);
            remoteId = data.getPathSegments().get(data.getPathSegments().size() - 3);
            Uri.Builder server = new Uri.Builder().scheme(data.getScheme())
                    .encodedAuthority(data.getEncodedAuthority());
            for (int i = 0; i < data.getPathSegments().size() - 3; i++) {
                server.appendPath(data.getPathSegments().get(i));
            }
            url = server.build().toString();
            Log.e(TAG, "IHM invitation link, url: " + url + " , token: " + bearerToken + " , remoteId: " + remoteId);
        }
        // get project info to check we can connect
        return new DBProject(
                0, remoteId, password, name, url,
                email, null, type, 0L,
                null, false, DBProject.ACCESS_LEVEL_UNKNOWN,
                bearerToken
        );
    }

    private long addProjectToDb(DBProject project) {
        long pid = db.addProject(project);

        // to make it the selected project even if we got here because of a VIEW intent
        // this is supposed to be done in activity result in billListViewActivity
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        preferences.edit().putLong("selected_project", pid).apply();

        showToast(getString(R.string.project_added_success), Toast.LENGTH_LONG);

        Log.i(TAG, "PROJECT local id : " + pid + " : " + project);
        return pid;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d(TAG, "ACT CREATED");
        //ButterKnife.bind(this, getView());

        // hide the keyboard when this window gets the focus
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        defaultIhmUrl = getArguments().getString(PARAM_DEFAULT_IHM_URL);
        defaultNcUrl = getArguments().getString(PARAM_DEFAULT_NC_URL);
        if (savedInstanceState != null) {
            return; // Let Android restore the form instead of reapplying link credentials.
        }

        String defaultTypeId = getArguments().getString(PARAM_DEFAULT_PROJECT_TYPE);
        if (defaultTypeId != null) {
            if (defaultTypeId.equals(ProjectType.IHATEMONEY.getId())) {
                whereLocal.setChecked(false);
                whereIhm.setChecked(true);
                whereSpliit.setChecked(false);
            } else if (defaultTypeId.equals(ProjectType.LOCAL.getId())) {
                whereLocal.setChecked(true);
                whereIhm.setChecked(false);
                whereSpliit.setChecked(false);
            } else if (defaultTypeId.equals(ProjectType.SPLIIT.getId())) {
                whereLocal.setChecked(false);
                whereIhm.setChecked(false);
                whereSpliit.setChecked(true);
            }
        }

        showHideInputFields(true);

        if (ProjectType.IHATEMONEY.getId().equals(defaultTypeId)
                && isFormValid()
                && isInvitationLink(defaultIhmUrl)) {
            onPressOk();
        }

        newProjectId.setText(getArguments().getString(PARAM_DEFAULT_PROJECT_ID));

        String defaultPassword = getArguments().getString(PARAM_DEFAULT_PROJECT_PASSWORD);
        if (defaultPassword != null) {
            newProjectPassword.setText(getArguments().getString(PARAM_DEFAULT_PROJECT_PASSWORD));
            if (isFormValid()) {
                onPressOk();
            }
        }

        if (ProjectType.SPLIIT.getId().equals(defaultTypeId) && isFormValid()) {
            onPressOk();
        }

        showHideValidationButtons();
    }

    protected ProjectType getProjectType() {
        if (whereLocal.isChecked()) {
            return ProjectType.LOCAL;
        } else if (whereIhm.isChecked()) {
            return ProjectType.IHATEMONEY;
        } else if (whereSpliit.isChecked()) {
            return ProjectType.SPLIIT;
        } else {
            return ProjectType.LOCAL;
        }
    }
    protected String getRemoteId() {
        return newProjectId.getText().toString();
    }
    protected String getUrl() {
        String url = newProjectUrl.getText().toString().trim();
        if (!url.startsWith("http://") && !url.startsWith("https://") && isValidUrl("https://"+url)) {
            url = "https://" + url;
        }
        Log.v(TAG, "getUrl: " + url);
        return url;
    }
    protected boolean isValidUrl(String url) {
        return Patterns.WEB_URL.matcher(url).matches();
        //return (isHttpsUrl(url) || isHttpUrl(url));
    }
    protected boolean isInvitationLink(String url) {
        Uri data = Uri.parse(url);
        return ("https".equals(data.getScheme()) || "http".equals(data.getScheme()))
                && data.getPathSegments().size() >= 3
                && "join".equals(data.getPathSegments().get(data.getPathSegments().size() - 2));
    }
    protected String getPassword() {
        return newProjectPassword.getText().toString();
    }
    protected boolean getTodoCreate() {
        return whatTodoCreate.isChecked();
    }
    protected String getName() {
        return newProjectName.getText().toString();
    }
    protected String getEmail() {
        return newProjectEmail.getText().toString();
    }

    private final ICallback getRemoteInfoCallBack = new ICallback() {
        @Override
        public void onFinish() {
        }

        public void onFinish(String result, String message) {
            if (message.isEmpty()) {
                DBProject project = pendingRemoteProject != null
                        ? pendingRemoteProject : getProjectFromFields(false);
                long pid = addProjectToDb(project);
                listener.close(pid, true);
            } else {
                AlertDialog.Builder builder = new ThemedMaterialAlertDialogBuilder(requireContext());
                builder.setTitle(getString(R.string.simple_error));
                builder.setMessage(getString(R.string.error_project_connect_check, message));
                builder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                builder.show();
            }
        }

        @Override
        public void onScheduled() {
        }
    };

    private final IProjectCreationCallback createRemoteCallBack = new IProjectCreationCallback() {
        @Override
        public void onFinish() {
        }

        public void onFinish(String result, String message, boolean usePrivateApi) {
            if (message.isEmpty()) {
                saveRemoteProject(null, usePrivateApi);
                // listener.close(pid, false);
            } else {
                AlertDialog.Builder builder = new ThemedMaterialAlertDialogBuilder(requireContext());
                builder.setTitle(getString(R.string.simple_error));
                builder.setMessage(getString(R.string.error_create_remote_project_helper, message));
                // Set up the buttons
                builder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                builder.show();

                progress.dismiss();
            }
        }

        @Override
        public void onScheduled() {
        }
    };

    protected void showToast(CharSequence text, int duration) {
        Context context = getActivity();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    @SuppressLint("Range")
    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getActivity().getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    protected void importFromFile(Uri fileUri) {
        String content = null;
        try {
            Log.v(TAG, "file uri: "+fileUri);
            Log.v(TAG, "file name: "+getFileName(fileUri));
            String projectRemoteId = getFileName(fileUri).replaceAll("\\.csv$", "");
            InputStream inputStream = getActivity().getContentResolver().openInputStream(fileUri);

            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            // if we ever want to get file content :
            /*BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            StringBuilder stringBuilder = new StringBuilder();
            while ( (content = bufferedReader.readLine()) != null ) {
                stringBuilder.append(content + System.getProperty("line.separator"));
            }
            inputStream.close();
            content = stringBuilder.toString();
            Log.v(TAG, "CONTENT "+content);
            bufferedReader.close();*/
            try {
                boolean previousLineEmpty = false;
                String currentSection = null;
                int row = 0;
                int nbCols;
                String icon, color, categoryname, categoryid, currencyname, exchangeRate,
                    what, payer_name, owersStr, paymentmode, paymentmodeid, paymentmodename, comment;
                long timestamp;
                String dateStr;
                Date date;
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
                String mainCurrencyName = null;
                boolean payer_active;
                double amount, payer_weight;
                Map<String, Integer> columns = new HashMap<>();
                int c;
                List<DBPaymentMode> paymentModes = new ArrayList<>();
                List<DBCategory> categories = new ArrayList<>();
                List<DBCurrency> currencies = new ArrayList<>();
                List<DBBill> bills = new ArrayList<>();
                Map<String, Boolean> membersActive = new HashMap<>();
                Map<String, Double> membersweight = new HashMap<>();
                Map<Long, Long> categoryIdConv = new HashMap<>();
                Map<Long, Long> paymentModeIdConv = new HashMap<>();
                Map<Long, String> billRemoteIdToPayerName = new HashMap<>();
                Map<Long, String> billRemoteIdToOwerStr = new HashMap<>();
                List<Long> owerIds = new ArrayList<>();
                String[] owersArray;

                CSVReader reader = new CSVReader(inputStreamReader);
                String[] nextLine;
                while ((nextLine = reader.readNext()) != null) {
                    // check if all fields are empty
                    boolean allFieldsEmpty = true;
                    for (String field: nextLine) {
                        if (!"".equals(field)) {
                            allFieldsEmpty = false;
                            break;
                        }
                    }
                    if (allFieldsEmpty) {
                        previousLineEmpty = true;
                    } else if (row == 0 || previousLineEmpty) {
                        previousLineEmpty = false;
                        nbCols = nextLine.length;
                        columns.clear();
                        for (c = 0; c < nbCols; c++) {
                            columns.put(nextLine[c], c);
                        }
                        if (columns.containsKey("what") &&
                                columns.containsKey("amount") &&
                                (columns.containsKey("date") || columns.containsKey("timestamp")) &&
                                columns.containsKey("payer_name") &&
                                columns.containsKey("payer_weight") &&
                                columns.containsKey("owers")
                        ) {
                            currentSection = "bills";
                        } else if (columns.containsKey("icon") &&
                                columns.containsKey("color") &&
                                columns.containsKey("categoryid") &&
                                columns.containsKey("categoryname")
                        ) {
                            currentSection = "categories";
                        } else if (columns.containsKey("exchange_rate") &&
                                columns.containsKey("currencyname")
                        ) {
                            currentSection = "currencies";
                        } else {
                            showToast(getString(R.string.import_error_header, row), Toast.LENGTH_LONG);
                            return;
                        }
                    } else {
                        // normal line
                        previousLineEmpty = false;
                        if (currentSection.equals("categories")) {
                            icon = nextLine[columns.get("icon")];
                            color = nextLine[columns.get("color")];
                            categoryid = nextLine[columns.get("categoryid")];
                            categoryname = nextLine[columns.get("categoryname")];
                            categories.add(new DBCategory(0, Long.parseLong(categoryid), 0, categoryname, icon, color));
                        } else if (currentSection.equals("paymentmodes")) {
                            icon = nextLine[columns.get("icon")];
                            color = nextLine[columns.get("color")];
                            paymentmodeid = nextLine[columns.get("categoryid")];
                            paymentmodename = nextLine[columns.get("categoryname")];
                            paymentModes.add(new DBPaymentMode(0, Long.parseLong(paymentmodeid), 0, paymentmodename, icon, color));
                        } else if (currentSection.equals("currencies")) {
                            currencyname = nextLine[columns.get("currencyname")];
                            exchangeRate = nextLine[columns.get("exchange_rate")];
                            if (Double.parseDouble(exchangeRate) == 1.0) {
                                mainCurrencyName = currencyname;
                            }
                            currencies.add(new DBCurrency(0, 0, 0, currencyname, Double.parseDouble(exchangeRate), DBBill.STATE_OK));
                        } else if ("bills".equals(currentSection)) {
                            what = columns.containsKey("what") ? nextLine[columns.get("what")] : "";
                            comment = columns.containsKey("comment") ? nextLine[columns.get("comment")] : "";
                            amount = columns.containsKey("amount") ? Double.parseDouble(nextLine[columns.get("amount")]) : 0;
                            // get timestamp in priority
                            if (columns.containsKey("timestamp")) {
                                timestamp = Long.parseLong(nextLine[columns.get("timestamp")]);
                            } else if (columns.containsKey("date")) {
                                dateStr = nextLine[columns.get("date")];
                                try {
                                    date = sdf.parse(dateStr);
                                    timestamp = date.getTime() / 1000;
                                } catch (Exception e) {
                                    showToast(getString(R.string.import_error_date, row), Toast.LENGTH_LONG);
                                    return;
                                }
                            } else {
                                timestamp = 0;
                            }

                            payer_name = columns.containsKey("payer_name") ? nextLine[columns.get("payer_name")] : "";
                            payer_weight = columns.containsKey("payer_name") ? Double.parseDouble(nextLine[columns.get("payer_weight")]) : 1;
                            owersStr = columns.containsKey("owers") ? nextLine[columns.get("owers")] : "";
                            payer_active = columns.containsKey("payer_active") && nextLine[columns.get("payer_active")].equals("1");
                            categoryid = (columns.containsKey("categoryid") && !"".equals(nextLine[columns.get("categoryid")])) ? nextLine[columns.get("categoryid")] : "0";
                            paymentmodeid = (columns.containsKey("paymentmodeid") && !"".equals(nextLine[columns.get("paymentmodeid")])) ? nextLine[columns.get("paymentmodeid")] : "0";
                            paymentmode = columns.containsKey("paymentmode") ? nextLine[columns.get("paymentmode")] : null;

                            membersActive.put(payer_name, payer_active);
                            membersweight.put(payer_name, payer_weight);

                            if (owersStr.trim().length() == 0) {
                                showToast(getString(R.string.import_error_owers, row), Toast.LENGTH_LONG);
                                return;
                            }
                            // ignore "deleteMeIfYouWant" bills that are just there
                            // to make sure we add members
                            if (!"deleteMeIfYouWant".equals(what)) {
                                billRemoteIdToOwerStr.put((long) row, owersStr);
                                owersArray = owersStr.split(", ");
                                for (int i = 0; i < owersArray.length; i++) {
                                    if (owersArray[i].trim().length() == 0) {
                                        showToast(getString(R.string.import_error_owers, row), Toast.LENGTH_LONG);
                                        return;
                                    }
                                    if (!membersweight.containsKey(owersArray[i].trim())) {
                                        membersweight.put(owersArray[i].trim(), 1.0);
                                    }
                                }
                                bills.add(
                                    new DBBill(
                                        0, row, 0, 0, amount, timestamp, what,
                                        DBBill.STATE_OK, "n", paymentmode,
                                        Integer.parseInt(categoryid), comment, Integer.parseInt(paymentmodeid)
                                    )
                                );
                                billRemoteIdToPayerName.put((long) row, payer_name);
                            }
                        }
                    }
                    row++;
                }
                Map<String, Long> memberNameToId = new HashMap<>();
                // add project
                DBProject newProject = new DBProject(
                        0, projectRemoteId, "", projectRemoteId, null,
                        null, null, ProjectType.LOCAL, 0L,
                        mainCurrencyName, false, DBProject.ACCESS_LEVEL_UNKNOWN,
                        null
                );
                long pid = db.addProject(newProject);
                Log.v(TAG, "NEW PROJECT ID : "+pid);

                // add payment modes
                for (DBPaymentMode pm: paymentModes) {
                    long pmDbId = db.addPaymentMode(new DBPaymentMode(0, pm.getRemoteId(), pid, pm.getName(), pm.getIcon(), pm.getColor()));
                    paymentModeIdConv.put(pm.getRemoteId(), pmDbId);
                }
                // add categories
                for (DBCategory cat: categories) {
                    long catDbId = db.addCategory(new DBCategory(0, cat.getRemoteId(), pid, cat.getName(), cat.getIcon(), cat.getColor()));
                    categoryIdConv.put(cat.getRemoteId(), catDbId);
                }
                // add currencies
                for (DBCurrency cur: currencies) {
                    long currDbId = db.addCurrency(new DBCurrency(0, 0, pid, cur.getName(), cur.getExchangeRate(), DBBill.STATE_OK));
                }
                // add members
                for (String mName: membersweight.keySet()) {
                    long memberDbId = db.addMember(
                        new DBMember(
                            0, 0, pid, mName,
                            membersActive.get(mName), membersweight.get(mName), DBBill.STATE_OK,
                            null, null, null, null, null
                        )
                    );
                    memberNameToId.put(mName, memberDbId);
                }
                // add bills
                for (DBBill b: bills) {
                    String payerName = billRemoteIdToPayerName.get(b.getRemoteId());
                    long payerId = memberNameToId.get(payerName);

                    owerIds.clear();
                    owersArray = billRemoteIdToOwerStr.get(b.getRemoteId()).split(", ");
                    for (int i = 0; i < owersArray.length; i++) {
                        owerIds.add(memberNameToId.get(owersArray[i]));
                    }
                    long billDbId = db.addBill(
                        new DBBill(
                            0, 0, pid, payerId, b.getAmount(),
                            b.getTimestamp(), b.getWhat(), DBBill.STATE_OK, "n",
                            b.getPaymentMode(), b.getCategoryRemoteId(), b.getComment(),
                            b.getPaymentModeRemoteId()
                        )
                    );
                    // add bill owers
                    for (Long owerId: owerIds) {
                        db.addBillower(billDbId, owerId);
                    }
                    listener.close(pid, false);
                }
            } catch (IOException e) {

            }
        } catch(FileNotFoundException ex) {
            Log.d(TAG, ex.getMessage());
        } catch(CsvValidationException ex) {
            Log.d(TAG, ex.getMessage());
        }
    }

}
