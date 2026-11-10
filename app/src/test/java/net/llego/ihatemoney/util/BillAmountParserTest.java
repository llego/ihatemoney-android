package net.llego.ihatemoney.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BillAmountParserTest {
    @Test
    public void parsesDecimalSeparatorsAndSignedAmounts() {
        assertEquals(12.50, BillAmountParser.parse("12.50"), 0.00001);
        assertEquals(12.50, BillAmountParser.parse(" 12,50 "), 0.00001);
        assertEquals(-4.25, BillAmountParser.parse("-4.25"), 0.00001);
        assertEquals(0, BillAmountParser.parse("0"), 0.00001);
        assertEquals(10000000, BillAmountParser.parse("1.0E7"), 0.00001);
    }

    @Test
    public void preservesCalculatorExpressions() {
        assertEquals(15, BillAmountParser.parse("(10+20)/2"), 0.00001);
        assertEquals(7.50, BillAmountParser.parse("2,5*3"), 0.00001);
    }

    @Test
    public void rejectsInvalidInputInsteadOfSavingZero() {
        for (String input : new String[]{null, "", " ", ".", "1..2", "12+", "words", "NaN", "Infinity", "1/0", "0/0"}) {
            assertNull("Should reject: " + input, BillAmountParser.parse(input));
        }
    }
}
