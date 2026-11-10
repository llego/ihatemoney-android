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
}
