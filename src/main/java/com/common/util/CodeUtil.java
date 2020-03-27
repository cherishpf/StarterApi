package com.common.util;

import java.util.Random;

/**
 * @author ding
 */
public class CodeUtil {
    private static Random random;

    static {
        random = new Random();
    }

    /**
     * Generate the unique 24-number code: yyMMdd + nano_time(15) + 000
     */
    public static String getCode() {
        String timeStr = String.format("%015d", System.nanoTime());
        return String.format("%s%s%d", DateUtil.getTodayStr("yyMMdd"), timeStr, random.nextInt(1000));
    }
}
