package net.eneiluj.moneybuster.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Compact English dates for expense rows, for example "6th July". */
public final class BillDateFormatter {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH);

    private BillDateFormatter() {}

    public static String format(String isoDate) {
        try {
            LocalDate date = LocalDate.parse(isoDate);
            int day = date.getDayOfMonth();
            String suffix = "th";
            if (day < 11 || day > 13) {
                switch (day % 10) {
                    case 1: suffix = "st"; break;
                    case 2: suffix = "nd"; break;
                    case 3: suffix = "rd"; break;
                }
            }
            return day + suffix + " " + date.format(MONTH);
        } catch (DateTimeParseException e) {
            return isoDate;
        }
    }
}
