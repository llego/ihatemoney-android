package net.llego.ihatemoney.util;

import java.time.LocalDate;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Localized date formatting for expense rows.
 *
 * <p>Uses each locale's natural date order and inflected month form (e.g. Swedish
 * {@code 6 juli}, US English {@code July 6}, Russian {@code 6 июля}). The year is shown
 * only when the expense is not in the current year.</p>
 */
public final class BillDateFormatter {
    private BillDateFormatter() {}

    public static String format(String isoDate) {
        return format(isoDate, Locale.getDefault(), LocalDate.now());
    }

    static String format(String isoDate, Locale locale, LocalDate today) {
        if (isoDate == null) return null;
        try {
            LocalDate date = LocalDate.parse(isoDate);
            boolean withYear = date.getYear() != today.getYear();
            String pattern = localizedPattern(locale, withYear);
            return date.format(DateTimeFormatter.ofPattern(pattern, locale));
        } catch (DateTimeParseException e) {
            return isoDate;
        }
    }

    private static String localizedPattern(Locale locale, boolean withYear) {
        String longPattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.LONG, null, IsoChronology.INSTANCE, locale);
        if (withYear) {
            return longPattern;
        }
        return stripYearPattern(longPattern);
    }

    /**
     * Removes the calendar-year field from a localized {@code DateTimeFormatter} pattern,
     * along with any literal separators attached to it (e.g. {@code ", y"}, {@code "y年"},
     * {@code " y 'г.'"}, {@code "y. "}, {@code " 'de' y"}), leaving day+month in the
     * locale's natural order.
     */
    private static String stripYearPattern(String pattern) {
        List<String> tokens = tokenize(pattern);
        int n = tokens.size();
        boolean[] skip = new boolean[n];

        for (int i = 0; i < n; i++) {
            String t = tokens.get(i);
            if (isField(t) && isYearField(t)) {
                skip[i] = true;
            }
        }

        // Flood through consecutive literal separators attached to a dropped year field.
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < n; i++) {
                if (skip[i] || isField(tokens.get(i))) continue;
                boolean adjacentToSkip = (i > 0 && skip[i - 1]) || (i < n - 1 && skip[i + 1]);
                if (adjacentToSkip) {
                    skip[i] = true;
                    changed = true;
                }
            }
        } while (changed);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (!skip[i]) sb.append(tokens.get(i));
        }
        return sb.toString().replaceAll("\\s{2,}", " ").trim();
    }

    private static List<String> tokenize(String pattern) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < pattern.length()) {
            if (asciiLetter(pattern.charAt(i))) {
                int j = i;
                while (j < pattern.length() && asciiLetter(pattern.charAt(j))) j++;
                tokens.add(pattern.substring(i, j));
                i = j;
            } else {
                int j = i;
                while (j < pattern.length() && !asciiLetter(pattern.charAt(j))) j++;
                tokens.add(pattern.substring(i, j));
                i = j;
            }
        }
        return tokens;
    }

    private static boolean asciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isField(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (!asciiLetter(token.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isYearField(String field) {
        char c = field.charAt(0);
        return c == 'y' || c == 'Y' || c == 'u';
    }
}