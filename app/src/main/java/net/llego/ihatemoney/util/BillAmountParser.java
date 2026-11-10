package net.llego.ihatemoney.util;

import net.objecthunter.exp4j.ExpressionBuilder;

public final class BillAmountParser {
    private BillAmountParser() {}

    /** Accept decimal commas and existing calculator expressions, but never silently save invalid input as zero. */
    public static Double parse(String input) {
        if (input == null) return null;
        String amount = input.trim().replace(',', '.');
        try {
            double value = Double.parseDouble(amount);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            // Existing bills can contain scientific notation; expressions are the fallback.
        }
        if (!amount.matches("[0-9.()\\-+/*]+")) return null;
        try {
            double value = new ExpressionBuilder(amount).build().evaluate();
            return Double.isFinite(value) ? value : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
