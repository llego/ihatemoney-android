package net.llego.ihatemoney.util;

import java.text.NumberFormat;
import java.util.Locale;

/** Display formatting only; storage, input parsing, and API/export values keep their precision. */
public final class MoneyFormatter {
    private MoneyFormatter() {}

    public static NumberFormat createDisplayFormat(Locale locale) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format;
    }

    /**
     * Like {@link #createDisplayFormat(Locale)} but without digit grouping, so the value
     * round-trips through the amount input field (grouped "1 234,56" would not re-parse).
     */
    public static NumberFormat createEditFormat(Locale locale) {
        NumberFormat format = createDisplayFormat(locale);
        format.setGroupingUsed(false);
        return format;
    }
}
