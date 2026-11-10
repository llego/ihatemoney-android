package net.llego.ihatemoney.theme;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.nextcloud.android.common.ui.color.ColorUtil;
import com.nextcloud.android.common.ui.theme.MaterialSchemes;
import com.nextcloud.android.common.ui.theme.ViewThemeUtilsBase;
import com.nextcloud.android.common.ui.theme.utils.AndroidViewThemeUtils;
import com.nextcloud.android.common.ui.theme.utils.AndroidXViewThemeUtils;
import com.nextcloud.android.common.ui.theme.utils.DialogViewThemeUtils;
import com.nextcloud.android.common.ui.theme.utils.MaterialViewThemeUtils;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.util.ColorUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/* Taken from Nextcloud Deck */
public class ThemeUtils extends ViewThemeUtilsBase {

    private static final ConcurrentMap<Integer, ThemeUtils> CACHE = new ConcurrentHashMap<>();

    public final AndroidViewThemeUtils platform;
    public final MaterialViewThemeUtils material;
    public final AndroidXViewThemeUtils androidx;
    public final DialogViewThemeUtils dialog;
    public final MoneyBusterViewThemeUtils moneybuster;

    private ThemeUtils(
            final MaterialSchemes schemes,
            final ColorUtil colorUtil
    ) {
        super(schemes);

        this.platform = new AndroidViewThemeUtils(schemes, colorUtil);
        this.material = new MaterialViewThemeUtils(schemes, colorUtil);
        this.androidx = new AndroidXViewThemeUtils(schemes, this.platform);
        this.dialog = new DialogViewThemeUtils(schemes);
        this.moneybuster = new MoneyBusterViewThemeUtils(this.material, schemes);
    }

    public static ThemeUtils of(@ColorInt int color, @NonNull Context context) {
        return CACHE.computeIfAbsent(color, c -> new ThemeUtils(
                MaterialSchemes.Companion.fromColor(c),
                new ColorUtil(context)
        ));
    }

    public static ThemeUtils of(@NonNull Context context) {
        int color = ColorUtils.primaryColor(context);
        return ThemeUtils.of(color, context);
    }

    /**
     * Creates a ThemeUtils using the default brand color (Nextcloud-blue).
     * Use this if no custom color is available.
     */
    public static ThemeUtils ofDefaultBrand(@NonNull Context context) {
        return of(ContextCompat.getColor(context, R.color.default_brand), context);
    }

}
