package net.llego.ihatemoney.util;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class BillDateFormatterTest {
    private static final Locale SV = Locale.forLanguageTag("sv-SE");
    private static final Locale EN = Locale.US;

    @Test
    public void swedishDateUsesLocalizedMonthAndOrder() {
        assertEquals("6 juli", BillDateFormatter.format("2026-07-06", SV, LocalDate.of(2026, 1, 1)));
    }

    @Test
    public void englishDateUsesMonthFirstOrder() {
        assertEquals("July 6", BillDateFormatter.format("2026-07-06", EN, LocalDate.of(2026, 1, 1)));
    }

    @Test
    public void currentYearOmitsYear() {
        assertEquals("6 juli", BillDateFormatter.format("2026-07-06", SV, LocalDate.of(2026, 11, 10)));
        assertEquals("July 6", BillDateFormatter.format("2026-07-06", EN, LocalDate.of(2026, 11, 10)));
    }

    @Test
    public void olderYearsIncludeTheYear() {
        assertEquals("6 juli 2025", BillDateFormatter.format("2025-07-06", SV, LocalDate.of(2026, 11, 10)));
        assertEquals("July 6, 2025", BillDateFormatter.format("2025-07-06", EN, LocalDate.of(2026, 11, 10)));
    }

    @Test
    public void inflectedLocalesUseTheFormatMonthForm() {
        assertEquals("6 июля", BillDateFormatter.format("2026-07-06", Locale.forLanguageTag("ru"), LocalDate.of(2026, 11, 10)));
    }

    @Test
    public void otherLocalesKeepTheirNaturalOrder() {
        assertEquals("július 6.", BillDateFormatter.format("2026-07-06", Locale.forLanguageTag("hu"), LocalDate.of(2026, 11, 10)));
        assertEquals("7月6日", BillDateFormatter.format("2026-07-06", Locale.forLanguageTag("ja"), LocalDate.of(2026, 11, 10)));
    }

    @Test
    public void publicHelperUsesSystemLocaleAndToday() {
        assertFalse(BillDateFormatter.format("2026-07-06").isEmpty());
    }

    @Test
    public void keepsInvalidDatesReadableInsteadOfCrashing() {
        assertEquals("invalid", BillDateFormatter.format("invalid"));
        assertEquals("2026-02-30", BillDateFormatter.format("2026-02-30"));
    }
}