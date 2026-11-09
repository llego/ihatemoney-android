package net.eneiluj.moneybuster.util;

import org.junit.Test;

import java.text.NumberFormat;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MoneyFormatterTest {
    @Test
    public void swedishAndFinnishAmountsAlwaysHaveTwoDecimals() {
        for (Locale locale : new Locale[]{Locale.forLanguageTag("sv-SE"), Locale.forLanguageTag("fi-FI")}) {
            NumberFormat format = MoneyFormatter.createDisplayFormat(locale);
            assertEquals("2,50", format.format(2.5));
            assertEquals("2,00", format.format(2));
            assertEquals("0,00", format.format(0));
            assertEquals("0,05", format.format(0.05));
            // Some locales include a directional mark or use a typographic minus sign.
            assertTrue(format.format(-2.5).endsWith("2,50"));
        }
    }

    @Test
    public void englishAmountsUseTheLocalesDecimalSeparator() {
        NumberFormat format = MoneyFormatter.createDisplayFormat(Locale.US);
        assertEquals("2.50", format.format(2.5));
        assertEquals("2.00", format.format(2));
        assertEquals("-2.50", format.format(-2.5));
        assertEquals("1,234.50", format.format(1234.5));
    }

    @Test
    public void extraPrecisionIsRoundedForDisplayOnly() {
        double amount = 2.567;
        assertEquals("2,57", MoneyFormatter.createDisplayFormat(Locale.forLanguageTag("sv-SE")).format(amount));
        assertEquals(2.567, amount, 0.0);
    }
}
