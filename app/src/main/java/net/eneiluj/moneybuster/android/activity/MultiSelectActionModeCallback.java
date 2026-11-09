package net.eneiluj.moneybuster.android.activity;

import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.view.ActionMode;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.model.DBBill;
import net.eneiluj.moneybuster.model.DBProject;
import net.eneiluj.moneybuster.model.Item;
import net.eneiluj.moneybuster.model.ItemAdapter;
import net.eneiluj.moneybuster.persistence.MoneyBusterSQLiteOpenHelper;

/** Contextual multi-select actions for the expense list (select all / delete). */
final class MultiSelectActionModeCallback implements ActionMode.Callback {

    private final Activity activity;
    private final ItemAdapter adapter;
    private final MoneyBusterSQLiteOpenHelper db;
    private final Runnable onRefresh;
    private final Runnable onClearSearch;
    private final Runnable onDestroy;

    MultiSelectActionModeCallback(Activity activity, ItemAdapter adapter,
                                  MoneyBusterSQLiteOpenHelper db,
                                  Runnable onRefresh, Runnable onClearSearch,
                                  Runnable onDestroy) {
        this.activity = activity;
        this.adapter = adapter;
        this.db = db;
        this.onRefresh = onRefresh;
        this.onClearSearch = onClearSearch;
        this.onDestroy = onDestroy;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.menu_list_context_multiple, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (item.getItemId() == R.id.menu_delete) {
            for (Integer i : adapter.getSelected()) {
                DBBill bill = (DBBill) adapter.getItem(i);

                // check if we can delete bills
                final DBProject project = db.getProject(bill.getProjectId());
                if (project.isDeletionDisabled()) {
                    Toast.makeText(activity, R.string.bill_deletion_is_disabled, Toast.LENGTH_SHORT).show();
                    mode.finish();
                    return true;
                }

                // get up to date bill
                final DBBill dbBill = db.getBill(bill.getId());
                // get real original state to potentially restore it
                final int originalState = dbBill.getState();

                if (originalState == DBBill.STATE_ADDED) {
                    db.deleteBill(dbBill.getId());
                } else {
                    db.setBillState(dbBill.getId(), DBBill.STATE_DELETED);
                }
            }
            mode.finish();
            onClearSearch.run();
            onRefresh.run();
            return true;
        }

        if (item.getItemId() == R.id.menu_select_all) {
            adapter.clearSelection();
            for (int i = 0; i < adapter.getItemCount(); i++) {
                adapter.select(i);
            }
            adapter.notifyDataSetChanged();
            int checkedItemCount = adapter.getSelected().size();
            mode.setTitle(activity.getResources().getQuantityString(R.plurals.ab_selected, checkedItemCount, checkedItemCount));
            return true;
        }

        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        onDestroy.run();
    }
}