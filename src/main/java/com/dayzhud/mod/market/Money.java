package com.dayzhud.mod.market;

/** Rouble formatting, used on both sides. */
public final class Money {

    public static final String SYMBOL = "\u20BD";

    private Money() {}

    /** "1 234 567" - space-grouped, the way roubles are written. */
    public static String format(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) out.append(' ');
            out.append(digits.charAt(i));
        }
        return (value < 0 ? "-" : "") + out;
    }

    /** "\u20BD1 234 567" */
    public static String withSymbol(long value) {
        return SYMBOL + format(value);
    }
}
