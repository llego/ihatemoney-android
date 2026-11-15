package net.llego.ihatemoney.android.dialogs;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.android.ui.TextDrawable;
import net.llego.ihatemoney.databinding.DialogEditMemberBinding;
import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.model.DBMember;
import net.llego.ihatemoney.model.DBProject;
import net.llego.ihatemoney.model.ProjectType;
import net.llego.ihatemoney.persistence.MoneyBusterSQLiteOpenHelper;
import net.llego.ihatemoney.persistence.SpliitMemberStore;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.llego.ihatemoney.theme.ThemeUtils;
import net.llego.ihatemoney.theme.ThemedMaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** Member dialogs owned by one activity; list refreshes and navigation remain with the caller. */
public final class MemberDialogs {
    public interface Listener {
        void onMemberAdded(long projectId, String name);

        void onMemberUpdated();

        void onMemberDeleted();
    }

    private final Activity activity;
    private final MoneyBusterSQLiteOpenHelper db;
    private final Listener listener;

    public MemberDialogs(Activity activity, MoneyBusterSQLiteOpenHelper db, Listener listener) {
        this.activity = activity;
        this.db = db;
        this.listener = listener;
    }

    public void showMembers(long projectId) {
        if (projectId == 0) return;
        DBProject project = db.getProject(projectId);
        if (project == null || (!project.isReadOnly() && !canManage(project))) return;

        List<DBMember> members = db.getMembersOfProject(projectId, null);
        List<String> names = new ArrayList<>();
        for (DBMember member : members) names.add(member.getName());
        ThemedMaterialAlertDialogBuilder builder = new ThemedMaterialAlertDialogBuilder(activity);
        builder
                .setTitle(R.string.fab_manage_members)
                .setItems(names.toArray(new CharSequence[0]), (dialog, which) -> {
                    if (project.getType() == ProjectType.SPLIIT) showSpliitMember(project, members.get(which));
                    else showEdit(project, members.get(which).getId());
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.simple_cancel, null);
        if (project.getType() != ProjectType.SPLIIT) {
            builder.setNeutralButton(R.string.fab_add_member, (dialog, which) -> showAdd(projectId));
        }
        builder.show();
    }

    private void showSpliitMember(DBProject project, DBMember member) {
        SpliitMemberStore store = new SpliitMemberStore(db);
        String conflict = store.get(member.getId(), SpliitMemberStore.CONFLICT);
        if (conflict != null) {
            if ("null".equals(conflict)) { showToast(R.string.spliit_member_needs_web); return; }
            JsonObject remote = JsonParser.parseString(conflict).getAsJsonObject();
            if (!remote.has("name") || remote.get("name").isJsonNull()) {
                showToast(R.string.spliit_member_needs_web);
                return;
            }
            new ThemedMaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.spliit_member_conflict_title)
                    .setMessage(activity.getString(R.string.spliit_member_conflict_message,
                            member.getName(), remote.get("name").getAsString()))
                    .setPositiveButton(R.string.spliit_keep_local, (dialog, which) ->
                            resolveSpliitRename(project, member, store, conflict, true))
                    .setNegativeButton(R.string.spliit_use_server, (dialog, which) ->
                            resolveSpliitRename(project, member, store, conflict, false))
                    .setNeutralButton(R.string.spliit_later, null).show();
            return;
        }
        EditText input = new EditText(new ContextThemeWrapper(activity, R.style.AppTheme));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(member.getName());
        new ThemedMaterialAlertDialogBuilder(activity).setTitle(R.string.edit_member_dialog_title)
                .setView(input).setPositiveButton(R.string.simple_ok, (dialog, which) -> {
                    String name = input.getText().toString();
                    if (name.length() < 2 || name.length() > 50 || name.trim().isEmpty()) {
                        showToast(R.string.spliit_member_invalid_name);
                    } else if (!name.equals(member.getName())) {
                        if (store.queueRename(member, name)) {
                            db.syncIfRemote(project);
                            listener.onMemberUpdated();
                        } else showToast(R.string.spliit_member_stale);
                    }
                    hideKeyboard(input.getContext());
                }).setNegativeButton(R.string.simple_cancel, null).show();
    }

    private void resolveSpliitRename(DBProject project, DBMember member, SpliitMemberStore store,
                                     String conflict, boolean keepLocal) {
        if (store.resolveRename(member.getId(), conflict, keepLocal)) {
            db.syncIfRemote(project);
            listener.onMemberUpdated();
        } else showToast(R.string.spliit_member_stale);
    }

    public void showAdd(long projectId) {
        if (projectId == 0) return;
        DBProject project = db.getProject(projectId);
        if (!canManage(project)) return;

        EditText input = new EditText(new ContextThemeWrapper(activity, R.style.AppTheme));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setTextColor(ContextCompat.getColor(activity, R.color.fg_default));
        new ThemedMaterialAlertDialogBuilder(activity)
                .setTitle(R.string.add_member_dialog_title)
                .setView(input)
                .setPositiveButton(R.string.simple_ok, (dialog, which) -> {
                    String name = input.getText().toString();
                    if (!name.equals("")) {
                        List<DBMember> members = db.getMembersOfProject(projectId, null);
                        List<String> names = new ArrayList<>();
                        for (DBMember member : members) names.add(member.getName());
                        if (!names.contains(name)) {
                            db.addMemberAndSync(new DBMember(0, 0, projectId, name,
                                    true, 1, DBBill.STATE_ADDED, null, null, null, null, null));
                            listener.onMemberAdded(projectId, name);
                        } else {
                            showToast(R.string.member_already_exists);
                        }
                    } else {
                        showToast(R.string.member_edit_empty_name);
                    }
                    hideKeyboard(input.getContext());
                })
                .setNegativeButton(R.string.simple_cancel, (dialog, which) -> {
                    dialog.cancel();
                    hideKeyboard(input.getContext());
                })
                .show();
        input.setSelectAllOnFocus(true);
        input.requestFocus();
        showKeyboard(activity);
    }

    private void showEdit(DBProject project, long memberId) {
        DBMember member = db.getMember(memberId);
        Integer red = member.getR();
        Integer green = member.getG();
        Integer blue = member.getB();
        int color = red != null && green != null && blue != null
                ? Color.rgb(red, green, blue) : TextDrawable.getColorFromName(member.getName());
        // Keep draft color scoped to this dialog rather than the host activity.
        int[] selectedColor = {color};

        DialogEditMemberBinding binding = DialogEditMemberBinding.inflate(activity.getLayoutInflater());
        binding.editMemberName.setText(member.getName());
        binding.editMemberName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        binding.editMemberName.setTextColor(ContextCompat.getColor(activity, R.color.fg_default));
        binding.editMemberWeight.setText(String.valueOf(member.getWeight()));
        binding.editMemberWeight.setTextColor(ContextCompat.getColor(activity, R.color.fg_default));
        binding.editMemberActivated.setChecked(member.isActivated());
        binding.editMemberColor.setBackgroundColor(color);
        binding.editMemberColor.setOnClickListener(view ->
                ColorPickerDialog.show(activity, color, newColor -> {
                    selectedColor[0] = newColor;
                    binding.editMemberColor.setBackgroundColor(newColor);
                }));

        AlertDialog dialog = new ThemedMaterialAlertDialogBuilder(
                new ContextThemeWrapper(activity, R.style.AppThemeDialog))
                .setTitle(R.string.edit_member_dialog_title)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.simple_ok, (selectedDialog, which) -> {
                    String name = binding.editMemberName.getText().toString();
                    double weight;
                    try {
                        weight = Double.parseDouble(binding.editMemberWeight.getText().toString().replace(',', '.'));
                    } catch (Exception e) {
                        showToast(R.string.member_edit_weight_error);
                        return;
                    }
                    long selectedProjectId = PreferenceManager.getDefaultSharedPreferences(activity)
                            .getLong("selected_project", 0);
                    if (selectedProjectId != 0) {
                        if (!name.isEmpty()) {
                            db.updateMemberAndSync(member, name, weight, binding.editMemberActivated.isChecked(),
                                    Color.red(selectedColor[0]), Color.green(selectedColor[0]),
                                    Color.blue(selectedColor[0]), "", "");
                            listener.onMemberUpdated();
                        } else {
                            showToast(R.string.member_edit_empty_name);
                        }
                    }
                    hideKeyboard(binding.getRoot().getContext());
                })
                .setNegativeButton(R.string.simple_cancel, (selectedDialog, which) -> {
                    selectedDialog.cancel();
                    hideKeyboard(binding.getRoot().getContext());
                })
                .show();

        boolean presentInExpenses = !db.getBillsOfMember(memberId).isEmpty();
        if (project.getType() != ProjectType.LOCAL) {
            binding.editMemberDelete.setEnabled(false);
            binding.editMemberDeleteHelp.setVisibility(View.VISIBLE);
            binding.editMemberDeleteHelp.setText(R.string.member_edit_delete_only_local_project_supported);
        } else if (presentInExpenses) {
            binding.editMemberDelete.setEnabled(false);
            binding.editMemberDeleteHelp.setVisibility(View.VISIBLE);
            binding.editMemberDeleteHelp.setText(R.string.member_edit_delete_cannot_delete);
        } else {
            binding.editMemberDelete.setEnabled(true);
            binding.editMemberDelete.setOnClickListener(view -> {
                db.deleteMember(memberId);
                listener.onMemberDeleted();
                dialog.dismiss();
            });
            binding.editMemberDeleteHelp.setVisibility(View.GONE);
        }

        var utils = ThemeUtils.of(activity);
        utils.material.colorTextInputLayout(binding.editMemberNameWrapper);
        utils.material.colorTextInputLayout(binding.editMemberWeightWrapper);
        utils.material.colorMaterialButtonPrimaryFilled(binding.editMemberDelete);
        utils.platform.themeCheckbox(binding.editMemberActivated);

        binding.editMemberName.setSelectAllOnFocus(true);
        binding.editMemberName.requestFocus();
        showKeyboard(binding.getRoot().getContext());
    }

    private boolean canManage(DBProject project) {
        if (project.isReadOnly()) {
            return false;
        }
        int access = project.getMyAccessLevel();
        if (access != DBProject.ACCESS_LEVEL_UNKNOWN && access < DBProject.ACCESS_LEVEL_MAINTAINER) {
            showToast(R.string.insufficient_access_level);
            return false;
        }
        return true;
    }

    private void showToast(int message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private void showKeyboard(Context context) {
        InputMethodManager keyboard = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    private void hideKeyboard(Context context) {
        InputMethodManager keyboard = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
    }
}
