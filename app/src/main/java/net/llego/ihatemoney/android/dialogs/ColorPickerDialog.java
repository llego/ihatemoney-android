package net.llego.ihatemoney.android.dialogs;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ScrollView;

import com.google.android.material.button.MaterialButton;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.theme.ThemedMaterialAlertDialogBuilder;

import java.util.function.IntConsumer;

/** Simple fixed-palette color chooser used for app themes and member colors. */
public final class ColorPickerDialog {
    private ColorPickerDialog() {}

    public static final int[] COLORS = {
            0xFFB3261E, 0xFFAD1457, 0xFF7B1FA2, 0xFF4E3C9A, 0xFF1565C0,
            0xFF006875, 0xFF00695C, 0xFF386A20, 0xFFB54708, 0xFF52606D
    };

    public static void show(Activity activity, int currentColor, IntConsumer onPicked) {
        String[] names = activity.getResources().getStringArray(R.array.theme_color_names);
        int spacing = (int) (8 * activity.getResources().getDisplayMetrics().density);
        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(2);
        grid.setPadding(spacing * 2, spacing, spacing * 2, spacing);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(grid);
        androidx.appcompat.app.AlertDialog dialog = new ThemedMaterialAlertDialogBuilder(activity)
                .setView(scroll)
                .setTitle(R.string.settings_colorpicker_title)
                .setNegativeButton(R.string.simple_cancel, null)
                .create();
        for (int i = 0; i < COLORS.length; i++) {
            final int color = COLORS[i];
            MaterialButton button = new MaterialButton(activity);
            button.setText(currentColor == color ? "✓ " + names[i] : names[i]);
            button.setSelected(currentColor == color);
            button.setTextColor(Color.WHITE);
            button.setBackgroundTintList(ColorStateList.valueOf(color));
            button.setMinHeight(spacing * 7);
            button.setAllCaps(false);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / 2), GridLayout.spec(i % 2, 1f));
            params.width = 0;
            params.setMargins(spacing / 2, spacing / 2, spacing / 2, spacing / 2);
            grid.addView(button, params);
            button.setOnClickListener((View v) -> {
                dialog.dismiss();
                onPicked.accept(color);
            });
        }
        dialog.show();
    }
}