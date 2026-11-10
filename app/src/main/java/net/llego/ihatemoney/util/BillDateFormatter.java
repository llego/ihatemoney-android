package net.llego.ihatemoney.util;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;

/** Localized compact dates for expense rows, e.g. "6 juli" (year shown when not the current one). */
public final class BillDateFormatter {
    private BillDateFormatter() {}

    public static String format(String isoDate) {
        return format(isoDate, Locale.getDefault(), LocalDate.now());
    }

    static String format(String isoDate, Locale locale, LocalDate today) {
        if (isoDate == null) return null;
        try {
            LocalDate date = LocalDate.parse(isoDate);
            String day = String.valueOf(date.getDayOfMonth());
            String month = date.getMonth().getDisplayName(TextStyle.FULL, locale);
            if (date.getYear() == today.getYear()) {
                return day + " " + month;
            }
            return day + " " + month + " " + date.getYear();
        } catch (DateTimeParseException e) {
            return isoDate;
        }
    }
}