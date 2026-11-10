package net.llego.ihatemoney.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
            // A localized pattern selects the inflected month form where the language inflects it
            // (e.g. Russian "6 июля"), and uses the locale's day/month ordering.
            DateTimeFormatter formatter = date.getYear() == today.getYear()
                    ? DateTimeFormatter.ofPattern("d MMMM", locale)
                    : DateTimeFormatter.ofPattern("d MMMM yyyy", locale);
            return date.format(formatter);
        } catch (DateTimeParseException e) {
            return isoDate;
        }
    }
}