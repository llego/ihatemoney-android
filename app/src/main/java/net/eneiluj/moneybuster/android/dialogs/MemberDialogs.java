package net.eneiluj.moneybuster.android.dialogs;

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

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.android.ui.TextDrawable;
import net.eneiluj.moneybuster.databinding.DialogColorBinding;
import net.eneiluj.moneybuster.databinding.DialogEditMemberBinding;
import net.eneiluj.moneybuster.model.DBBill;
import net.eneiluj.moneybuster.model.DBMember;
import net.eneiluj.moneybuster.model.DBProject;
import net.eneiluj.moneybuster.model.ProjectType;
import net.eneiluj.moneybuster.persistence.MoneyBusterSQLiteOpenHelper;
import net.eneiluj.moneybuster.theme.ThemeUtils;
import net.eneiluj.moneybuster.theme.ThemedMaterialAlertDialogBuilder;

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
        if (!canManage(project)) return;

        List<DBMember> members = db.getMembersOfProject(projectId, null);
        List<String> names = new ArrayList<>();
        for (DBMember member : members) names.add(member.getName());
        new ThemedMaterialAlertDialogBuilder(activity)
                .setTitle(R.string.fab_manage_members)
                .setItems(names.toArray(new CharSequence[0]), (dialog, which) -> {
                    showEdit(project, members.get(which).getId());
                    dialog.dismiss();
                })
                .setNeutralButton(R.string.fab_add_member, (dialog, which) -> showAdd(projectId))
                .setNegativeButton(R.string.simple_cancel, null)
                .show();
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
        binding.editMemberColor.setOnClickListener(view -> {
            DialogColorBinding colors = DialogColorBinding.inflate(activity.getLayoutInflater());
            colors.lobsterPicker.addDecorator(colors.shadeSlider);
            colors.lobsterPicker.setColorHistoryEnabled(true);
            colors.lobsterPicker.setHistory(color);
            colors.lobsterPicker.setColor(color);
            new ThemedMaterialAlertDialogBuilder(activity)
                    .setView(colors.getRoot())
                    .setTitle(R.string.settings_colorpicker_title)
                    .setPositiveButton(R.string.simple_ok, (dialog, which) -> {
                        selectedColor[0] = colors.lobsterPicker.getColor();
                        binding.editMemberColor.setBackgroundColor(selectedColor[0]);
                    })
                    .setNegativeButton(R.string.simple_cancel, null)
                    .show();
        });

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
