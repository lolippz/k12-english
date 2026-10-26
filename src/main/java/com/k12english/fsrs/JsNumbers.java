package com.k12english.fsrs;

import java.util.Locale;

/**
 * JS 的 {@code Number::toString} 等价实现。
 *
 * <p>为什么需要它：fuzz 的随机种子是
 * {@code `${time}_${reps}_${difficulty * stability}`}，字符串必须与 V8 完全一致，
 * 否则 Alea 会走出一条完全不同的序列。而 Java 17 的 {@code Double.toString}
 * 有两个坑：
 * <ul>
 *   <li>整数会带 {@code .0}（JS 输出 {@code "0"}，Java 输出 {@code "0.0"}）</li>
 *   <li>JDK 17 尚未采用最短往返算法（JDK-4511638 在 Java 19 才修），
 *       某些值会多出无效尾数，例如 JS 的 {@code 1.3596196} 可能变成
 *       {@code 1.3596196000000001}</li>
 * </ul>
 * 所以这里自己实现：先用 1~17 位有效数字试出最短往返表示，再按 ECMA-262
 * {@code Number::toString} 的规则排版。
 */
final class JsNumbers {

    private JsNumbers() {
    }

    static String toString(double v) {
        if (Double.isNaN(v)) return "NaN";
        if (Double.isInfinite(v)) return v > 0 ? "Infinity" : "-Infinity";
        if (v == 0) return "0";   // 含 -0.0，JS 也输出 "0"

        boolean negative = v < 0;
        double abs = Math.abs(v);

        // 1. 最短往返：找到最小的有效位数 p，使 p 位十进制能还原同一个 double
        String sci = null;
        for (int p = 1; p <= 17; p++) {
            String candidate = String.format(Locale.ROOT, "%." + (p - 1) + "e", abs);
            if (Double.parseDouble(candidate) == abs) {
                sci = candidate;
                break;
            }
        }
        if (sci == null) {
            sci = String.format(Locale.ROOT, "%.16e", abs);
        }

        // 2. 拆成 digits（有效数字串）与 n（小数点位置）
        int eIdx = sci.indexOf('e');
        String mantissa = sci.substring(0, eIdx);
        int exp = Integer.parseInt(sci.substring(eIdx + 1));
        String digits = mantissa.replace(".", "");
        int k = digits.length();
        int n = exp + 1;

        // 3. 按 ECMA-262 排版
        StringBuilder sb = new StringBuilder();
        if (negative) sb.append('-');
        if (k <= n && n <= 21) {
            sb.append(digits);
            sb.append("0".repeat(n - k));
        } else if (0 < n && n <= 21) {
            sb.append(digits, 0, n).append('.').append(digits.substring(n));
        } else if (-6 < n && n <= 0) {
            sb.append("0.").append("0".repeat(-n)).append(digits);
        } else {
            sb.append(digits.charAt(0));
            if (k > 1) sb.append('.').append(digits.substring(1));
            sb.append('e').append(n - 1 >= 0 ? '+' : '-').append(Math.abs(n - 1));
        }
        return sb.toString();
    }
}
