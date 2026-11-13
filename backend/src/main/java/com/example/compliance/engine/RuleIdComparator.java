package com.example.compliance.engine;

import java.math.BigInteger;
import java.util.Comparator;

/**
 * Orders rule ids "naturally": runs of digits compare by numeric value, everything else character by
 * character. For the zero-padded ids in the sample ({@code R001..R008}) this is identical to plain
 * string order, but it also keeps {@code R2} before {@code R10} if a customer does not pad their ids.
 * Ties (e.g. {@code R01} vs {@code R1}) fall back to plain string order so the ordering is total.
 */
public final class RuleIdComparator implements Comparator<String> {

    public static final RuleIdComparator INSTANCE = new RuleIdComparator();

    @Override
    public int compare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int endA = digitRunEnd(a, i);
                int endB = digitRunEnd(b, j);
                int cmp = new BigInteger(a.substring(i, endA)).compareTo(new BigInteger(b.substring(j, endB)));
                if (cmp != 0) {
                    return cmp;
                }
                i = endA;
                j = endB;
            } else {
                if (ca != cb) {
                    return Character.compare(ca, cb);
                }
                i++;
                j++;
            }
        }
        int remaining = Integer.compare(a.length() - i, b.length() - j);
        return remaining != 0 ? remaining : a.compareTo(b);
    }

    private static int digitRunEnd(String s, int start) {
        int end = start;
        while (end < s.length() && Character.isDigit(s.charAt(end))) {
            end++;
        }
        return end;
    }
}
