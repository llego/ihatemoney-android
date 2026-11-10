package net.llego.ihatemoney.android.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.net.Uri;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.databinding.ActivityEditProjectBinding;
import net.llego.ihatemoney.android.fragment.EditProjectFragment;
import net.llego.ihatemoney.android.dialogs.ProjectShareDialogBuilder;
import net.llego.ihatemoney.persistence.MoneyBusterSQLiteOpenHelper;
import net.llego.ihatemoney.theme.ThemedMaterialAlertDialogBuilder;
import net.llego.ihatemoney.util.ExportUtil;
import net.llego.ihatemoney.model.DBProject;
import net.llego.ihatemoney.util.EdgeToEdge;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class EditProjectActivity extends AppCompatActivity implements EditProjectFragment.EditProjectFragmentListener {

    public static final String PARAM_PROJECT_ID = "projectId";

    protected EditProjectFragment fragment;

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"), this::writeExport);

    @Override
    public void shareProject() {
        DBProject project = MoneyBusterSQLiteOpenHelper.getInstance(this).getProject(getProjectId());
        if (project != null && project.isShareable()) {
            new ProjectShareDialogBuilder(this, project).show();
        }
    }

    @Override
    public void exportProject() {
        exportLauncher.launch(ExportUtil.createExportFileName(
                MoneyBusterSQLiteOpenHelper.getInstance(this), getProjectId()));
    }

    private void writeExport(Uri uri) {
        if (uri == null) return;
        try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
            if (stream == null) throw new IOException("Cannot open export file");
            try (OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
                writer.write(ExportUtil.createExportContent(
                        MoneyBusterSQLiteOpenHelper.getInstance(this), getProjectId()));
            }
            showToast(getString(R.string.file_saved_success, uri.getLastPathSegment()), Toast.LENGTH_LONG);
        } catch (IOException e) {
            showToast(e.getMessage(), Toast.LENGTH_LONG);
        }
    }

    @Override
    public void removeProject() {
        MoneyBusterSQLiteOpenHelper db = MoneyBusterSQLiteOpenHelper.getInstance(this);
        DBProject project = db.getProject(getProjectId());
        if (project == null) return;
        ThemedMaterialAlertDialogBuilder builder = new ThemedMaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.confirm_remove_project_dialog_title);
        if (!project.isLocal()) builder.setMessage(R.string.confirm_remove_project_dialog_message);
        builder.setNegativeButton(R.string.simple_cancel, null);
        builder.setPositiveButton(R.string.project_remove_from_device, (dialog, which) -> {
            db.deleteProject(project.getId());
            String name = project.getName();
            showToast(getString(R.string.remove_project_confirmation,
                    name == null || name.isEmpty() ? project.getRemoteId() : name), Toast.LENGTH_LONG);
            closeOnDelete(project.getId());
        });
        builder.show();
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityEditProjectBinding binding = ActivityEditProjectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdge.apply(this, binding.getRoot());
        setSupportActionBar(binding.toolbar);

        if (savedInstanceState == null) {
            launchEditProjectFragment();
        } else {
            fragment = (EditProjectFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container_view);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(getClass().getSimpleName(), "onNewIntent: " + intent.getLongExtra(PARAM_PROJECT_ID, 0));
        setIntent(intent);
        if (fragment != null) {
            getSupportFragmentManager().beginTransaction().detach(fragment).commit();
            fragment = null;
        }
        launchEditProjectFragment();
    }

    protected long getProjectId() {
        return getIntent().getLongExtra(PARAM_PROJECT_ID, 0);
    }

    private void launchEditProjectFragment() {
        long projectId = getProjectId();
        Fragment.SavedState savedState = null;
        if (fragment != null) {
            savedState = getSupportFragmentManager().saveFragmentInstanceState(fragment);
        }
        fragment = EditProjectFragment.newInstance(projectId);
        if (savedState != null) {
            fragment.setInitialSavedState(savedState);
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container_view, fragment)
                .commit();
    }

    @Override
    public void onBackPressed() {
        close();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                close();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Send result and closes the Activity
     */
    public void close() {
        fragment.onCloseProject();
        finish();
    }

    public void closeOnDelete(long pid) {
        fragment.onCloseProject();
        final Intent data = new Intent();
        data.putExtra(BillsListViewActivity.DELETED_PROJECT, pid);
        setResult(RESULT_OK, data);
        Log.d(getClass().getSimpleName(), "setresult pid : "+pid);
        finish();
    }

    public void closeOnEdit(long pid) {
        fragment.onCloseProject();
        final Intent data = new Intent();
        data.putExtra(BillsListViewActivity.EDITED_PROJECT, pid);
        setResult(RESULT_OK, data);
        finish();
    }

    public void onProjectUpdated(DBProject project) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(project.getName());
            actionBar.setSubtitle(project.getServerUrl());
        }
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}
