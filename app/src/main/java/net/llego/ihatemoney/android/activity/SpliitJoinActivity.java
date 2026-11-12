package net.llego.ihatemoney.android.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.databinding.ActivitySpliitJoinBinding;
import net.llego.ihatemoney.model.DBProject;
import net.llego.ihatemoney.model.ProjectType;
import net.llego.ihatemoney.persistence.MoneyBusterSQLiteOpenHelper;
import net.llego.ihatemoney.theme.ThemedMaterialAlertDialogBuilder;
import net.llego.ihatemoney.theme.ThemedProgressDialogBuilder;
import net.llego.ihatemoney.util.EdgeToEdge;
import net.llego.ihatemoney.util.ICallback;
import net.llego.ihatemoney.util.SpliitUrl;

/**
 * Adds a Spliit group by its link. Spliit has no join form: the shared group
 * link is the group, so this screen only needs the link (or its QR code).
 */
public class SpliitJoinActivity extends AppCompatActivity {

    private ActivitySpliitJoinBinding binding;
    private MoneyBusterSQLiteOpenHelper db;
    private DBProject pendingProject;
    private boolean joining = false;
    private AlertDialog progress;

    private final ActivityResultLauncher<Intent> scanLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            Intent data = result.getData();
                            if (result.getResultCode() == RESULT_OK && data != null) {
                                String scanned = data.getStringExtra(QrCodeScannerActivity.KEY_QR_CODE);
                                if (scanned != null && !scanned.isEmpty()) {
                                    binding.editSpliitUrl.setText(scanned);
                                    join();
                                }
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySpliitJoinBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdge.apply(this, binding.getRoot());
        db = MoneyBusterSQLiteOpenHelper.getInstance(this);

        Toolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.scanButton.setOnClickListener(v ->
                scanLauncher.launch(new Intent(this, QrCodeScannerActivity.class)));
        binding.joinButton.setOnClickListener(v -> join());

        if (savedInstanceState == null) {
            handleIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return;
        }
        Uri data = intent.getData();
        if (data != null) {
            binding.editSpliitUrl.setText(data.toString());
            join();
        }
    }

    private void join() {
        if (joining) {
            return;
        }
        CharSequence text = binding.editSpliitUrl.getText();
        SpliitUrl url = SpliitUrl.parse(text == null ? null : text.toString());
        if (url == null) {
            binding.editSpliitUrlInputLayout.setError(getString(R.string.spliit_join_invalid));
            return;
        }
        binding.editSpliitUrlInputLayout.setError(null);
        hideKeyboard();

        pendingProject = new DBProject(
                0, url.groupId, "", "", url.baseUrl, "",
                null, ProjectType.SPLIIT, 0L, null,
                false, DBProject.ACCESS_LEVEL_UNKNOWN, null);

        joining = true;
        binding.joinButton.setEnabled(false);
        AlertDialog.Builder builder = new ThemedProgressDialogBuilder(this);
        builder.setTitle(getString(R.string.simple_loading));
        builder.setCancelable(false);
        progress = builder.show();
        if (!db.getMoneyBusterServerSyncHelper().getRemoteProjectInfo(pendingProject, callback)) {
            joining = false;
            binding.joinButton.setEnabled(true);
            dismissProgress();
            showToast(getString(R.string.error_no_network));
        }
    }

    private final ICallback callback = new ICallback() {
        @Override
        public void onFinish() {
        }

        @Override
        public void onFinish(String result, String message) {
            joining = false;
            dismissProgress();
            if (isFinishing() || binding == null) {
                return;
            }
            binding.joinButton.setEnabled(true);
            if (!message.isEmpty()) {
                new ThemedMaterialAlertDialogBuilder(SpliitJoinActivity.this)
                        .setTitle(R.string.simple_error)
                        .setMessage(getString(R.string.error_project_connect_check, message))
                        .setPositiveButton(R.string.simple_ok, null)
                        .show();
                return;
            }
            long pid = db.addProject(pendingProject);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            preferences.edit().putLong("selected_project", pid).apply();

            Intent data = new Intent();
            data.putExtra(BillsListViewActivity.ADDED_PROJECT, pid);
            setResult(RESULT_OK, data);
            finish();
        }

        @Override
        public void onScheduled() {
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissProgress();
    }

    private void dismissProgress() {
        if (progress != null && progress.isShowing()) {
            progress.dismiss();
        }
        progress = null;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && binding.editSpliitUrl.getWindowToken() != null) {
            imm.hideSoftInputFromWindow(binding.editSpliitUrl.getWindowToken(), 0);
        }
    }

    private void showToast(CharSequence text) {
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
    }
}
