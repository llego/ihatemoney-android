package net.llego.ihatemoney.theme;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.util.ColorUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Entry point for app theming; replaces the Nextcloud `ViewThemeUtilsBase`-based version. */
public final class ThemeUtils {

    private static final ConcurrentMap<Integer, ThemeUtils> CACHE = new ConcurrentHashMap<>();

    public final MoneyBusterMaterialUtils material;
    public final MoneyBusterPlatformUtils platform;
    public final MoneyBusterDialogUtils dialog;
    public final MoneyBusterViewThemeUtils moneybuster;

    private ThemeUtils(@NonNull MoneyBusterSchemes schemes) {
        this.material = new MoneyBusterMaterialUtils(schemes);
        this.platform = new MoneyBusterPlatformUtils(schemes);
        this.dialog = new MoneyBusterDialogUtils(schemes);
        this.moneybuster = new MoneyBusterViewThemeUtils(schemes);
    }

    public static ThemeUtils of(@ColorInt int color, @NonNull Context context) {
        return CACHE.computeIfAbsent(color, c -> new ThemeUtils(new MoneyBusterSchemes(c)));
    }

    public static ThemeUtils of(@NonNull Context context) {
        return of(ColorUtils.primaryColor(context), context);
    }

    /** A ThemeUtils using the default brand color, for views without a custom color. */
    public static ThemeUtils ofDefaultBrand(@NonNull Context context) {
        return of(ContextCompat.getColor(context, R.color.default_brand), context);
    }
}