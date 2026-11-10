package net.llego.ihatemoney.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BillDateFormatterTest {
    @Test
    public void formatsRequestedDayAndFullMonthWithoutYear() {
        assertEquals("6th July", BillDateFormatter.format("2026-07-06"));
        assertEquals("1st January", BillDateFormatter.format("2025-01-01"));
        assertEquals("31st December", BillDateFormatter.format("2024-12-31"));
    }

    @Test
    public void usesCorrectOrdinalForEveryDayOfTheMonth() {
        for (int day = 1; day <= 31; day++) {
            String suffix = "th";
            if (day == 1 || day == 21 || day == 31) suffix = "st";
            if (day == 2 || day == 22) suffix = "nd";
            if (day == 3 || day == 23) suffix = "rd";
            String isoDate = "2026-07-" + (day < 10 ? "0" : "") + day;
            assertEquals(day + suffix + " July", BillDateFormatter.format(isoDate));
        }
    }

    @Test
    public void keepsInvalidDatesReadableInsteadOfCrashing() {
        assertEquals("invalid", BillDateFormatter.format("invalid"));
        assertEquals("2026-02-30", BillDateFormatter.format("2026-02-30"));
    }
}
