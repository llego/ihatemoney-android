package net.llego.ihatemoney.android.ui;

import android.content.Context;
import android.view.View;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.databinding.ViewSyncProgressBinding;
import net.llego.ihatemoney.model.SyncProgress;

/** A non-modal status that remains visible above either the expense list or an empty state. */
public final class SyncProgressController {
    private final ViewSyncProgressBinding binding;

    public SyncProgressController(ViewSyncProgressBinding binding) { this.binding = binding; }

    public void show(SyncProgress progress) {
        if (progress == null) {
            binding.getRoot().setVisibility(View.GONE);
            return;
        }
        Context context = binding.getRoot().getContext();
        int label = switch (progress.stage) {
            case QUEUED -> R.string.sync_progress_queued;
            case PREPARING -> R.string.sync_progress_preparing;
            case PROJECT -> R.string.sync_progress_project;
            case LISTING -> R.string.sync_progress_listing;
            case DOWNLOADING -> R.string.sync_progress_downloading;
            case UPLOADING -> R.string.sync_progress_uploading;
            case SAVING -> R.string.sync_progress_saving;
        };
        String text = context.getString(label);
        if (progress.hasTotal()) {
            text = context.getString(R.string.sync_progress_count, text, progress.completed, progress.total);
        } else if (progress.stage == SyncProgress.Stage.LISTING && progress.completed > 0) {
            text = context.getResources().getQuantityString(R.plurals.sync_progress_found,
                    progress.completed, text, progress.completed);
        }
        binding.syncProgressText.setText(text);
        // Material indicators cannot switch to indeterminate mode while visible.
        boolean indeterminate = !progress.hasTotal();
        if (binding.syncProgressBar.isIndeterminate() != indeterminate) {
            binding.syncProgressBar.setVisibility(View.INVISIBLE);
            binding.syncProgressBar.setIndeterminate(indeterminate);
        }
        if (progress.hasTotal()) binding.syncProgressBar.setProgressCompat(progress.percent(), false);
        binding.syncProgressBar.setVisibility(View.VISIBLE);
        binding.getRoot().setVisibility(View.VISIBLE);
    }
}
