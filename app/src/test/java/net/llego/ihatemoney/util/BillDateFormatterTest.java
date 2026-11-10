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
    public void swedishDateUsesLocalizedMonthName() {
        assertEquals("6 juli", BillDateFormatter.format("2026-07-06", SV, LocalDate.of(2026, 1, 1)));
    }

    @Test
    public void englishDateUsesLocalizedMonthName() {
        assertEquals("6 July", BillDateFormatter.format("2026-07-06", EN, LocalDate.of(2026, 1, 1)));
    }

    @Test
    public void currentYearOmitsYear() {
        assertEquals("6 juli", BillDateFormatter.format("2026-07-06", SV, LocalDate.of(2026, 11, 10)));
    }

    @Test
    public void olderYearsIncludeTheYear() {
        assertEquals("6 juli 2025", BillDateFormatter.format("2025-07-06", SV, LocalDate.of(2026, 11, 10)));
        assertEquals("31 december 2024", BillDateFormatter.format("2024-12-31", SV, LocalDate.of(2026, 11, 10)));
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