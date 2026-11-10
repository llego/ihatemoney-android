package net.llego.ihatemoney.theme;

import androidx.fragment.app.Fragment;

import net.llego.ihatemoney.util.ColorUtils;

public abstract class ThemedFragment extends Fragment implements Themed {

    @Override
    public void onStart() {
        super.onStart();

        // FIXME: Use a LiveData to constantly observe this and react to changes? -> See Nextcloud Deck
        int color = ColorUtils.primaryColor(requireContext());
        applyTheme(color);
    }
}
