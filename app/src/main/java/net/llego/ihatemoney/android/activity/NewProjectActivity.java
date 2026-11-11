package net.llego.ihatemoney.android.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.android.fragment.NewProjectFragment;
import net.llego.ihatemoney.model.ProjectType;

import java.util.List;

public class NewProjectActivity extends AppCompatActivity implements NewProjectFragment.NewProjectFragmentListener {

    protected NewProjectFragment fragment;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Log.d(getClass().getSimpleName(), "onCreate: ");
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            launchNewProjectFragment();
        } else {
            fragment = (NewProjectFragment) getSupportFragmentManager().findFragmentById(android.R.id.content);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(getClass().getSimpleName(), "onNewIntent: ");
        setIntent(intent);
        if (fragment != null) {
            getSupportFragmentManager().beginTransaction().detach(fragment).commit();
            fragment = null;
        }
        launchNewProjectFragment();
    }

    protected String getDefaultIhmUrl() {
        return getIntent().getStringExtra(NewProjectFragment.PARAM_DEFAULT_IHM_URL);
    }

    protected String getDefaultNcUrl() {
        return getIntent().getStringExtra(NewProjectFragment.PARAM_DEFAULT_NC_URL);
    }

    private void launchNewProjectFragment() {
        String defaultIhmUrl = getDefaultIhmUrl();
        String defaultNcUrl = getDefaultNcUrl();
        String defaultProjectId = null;
        String defaultProjectPassword = null;
        ProjectType defaultProjectType = ProjectType.IHATEMONEY;
        if (defaultIhmUrl == null) {
            defaultIhmUrl = "https://ihatemoney.org";
        }

        Boolean shouldCloseActivity = false;

        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            Uri data = getIntent().getData();
            if (data == null) {
                showToast(getString(R.string.import_no_data), Toast.LENGTH_LONG);
                shouldCloseActivity = true;
            } else if (
                    data.getScheme() != null
                    && (data.getScheme().equals("ihatemoney") || data.getScheme().equals("ihatemoney+http"))
                    && data.getPathSegments().size() >= 1
            ) {
                // invitation link
                if (data.getPathSegments().size() >= 3
                    && "join".equals(data.getPathSegments().get(data.getPathSegments().size() - 2))) {
                    String protocol = "https";
                    if (data.getScheme().equals("ihatemoney+http")) {
                        protocol = "http";
                    }
                    defaultIhmUrl = protocol + "://" + data.getHost();
                    if (data.getPort() != -1) {
                        defaultIhmUrl += ":" + data.getPort();
                    }
                    defaultIhmUrl += data.getPath();
                    Log.e("PORT", "port is " + data.getPort());
                } else {
                    if (data.getPath().endsWith("/")) {
                        defaultProjectPassword = "";
                        defaultProjectId = data.getLastPathSegment();
                    } else {
                        defaultProjectPassword = data.getLastPathSegment();
                        defaultProjectId = data.getPathSegments().get(data.getPathSegments().size() - 2);
                    }
                    String protocol = "https";
                    if (data.getScheme().equals("ihatemoney+http")) {
                        protocol = "http";
                    }
                    defaultIhmUrl = protocol + "://" + data.getHost();
                    if (data.getPort() != -1) {
                        defaultIhmUrl += ":" + data.getPort();
                    }
                    defaultIhmUrl += data.getPath().replaceAll("/" + defaultProjectId + "/" + defaultProjectPassword + "$", "");
                }
                defaultProjectType = ProjectType.IHATEMONEY;
            } else if (data.getScheme() != null && data.getScheme().equals("spliit")) {
                // Spliit group link: spliit://<host>[:port]/<base-path...>/<group>;
                // also accept .../groups/<group>, matching the web URL shape.
                defaultProjectType = ProjectType.SPLIIT;
                StringBuilder base = new StringBuilder("https://").append(data.getHost());
                if (data.getPort() != -1) {
                    base.append(":").append(data.getPort());
                }
                List<String> segments = data.getPathSegments();
                int baseSegmentCount = segments.size() >= 2
                        && "groups".equals(segments.get(segments.size() - 2))
                        ? segments.size() - 2 : segments.size() - 1;
                for (int i = 0; i < baseSegmentCount; i++) {
                    base.append("/").append(segments.get(i));
                }
                defaultIhmUrl = base.toString();
                defaultProjectId = segments.isEmpty() ? null : data.getLastPathSegment();
            } else {
                showToast(getString(R.string.import_bad_url), Toast.LENGTH_LONG);
                shouldCloseActivity = true;
            }
        }
        fragment = NewProjectFragment.newInstance(
                defaultIhmUrl, defaultNcUrl,
                defaultProjectId, defaultProjectPassword, defaultProjectType,
                (Intent.ACTION_VIEW.equals(getIntent().getAction()))
        );

        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();

        if (shouldCloseActivity) {
            close(0, false);
        }
    }

    @Override
    public void onBackPressed() {
        close(0, false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                close(0, false);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Send result and closes the Activity
     */
    public void close(long pid, boolean justAdded) {
        fragment.onCloseProject();
        final Intent data = new Intent();
        if (justAdded) {
            data.putExtra(BillsListViewActivity.ADDED_PROJECT, pid);
        } else {
            data.putExtra(BillsListViewActivity.CREATED_PROJECT, pid);
        }
        setResult(RESULT_OK, data);
        finish();
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}
