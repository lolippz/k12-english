package com.k12english.fsrs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 学习步长策略，对齐 ts-fsrs 的 {@code ConvertStepUnitToMinutes} +
 * {@code BasicLearningStepsStrategy}。
 *
 * <p>步长字符串形如 {@code "1m"} / {@code "10m"} / {@code "1h"} / {@code "1d"}，
 * 分别表示分钟、小时、天。
 */
final class FsrsLearningSteps {

    private FsrsLearningSteps() {
    }

    /** 某一档评分对应的「几分钟后复习」与「跳到第几步」。 */
    record StepInfo(int scheduledMinutes, int nextStep) {
    }

    /** 把 {@code "10m"} 之类的步长换算成分钟。 */
    static int toMinutes(String step) {
        if (step == null || step.length() < 2) {
            throw new IllegalArgumentException("Invalid step value: " + step);
        }
        char unit = step.charAt(step.length() - 1);
        int value = parseLeadingInt(step.substring(0, step.length() - 1), step);
        if (value < 0) {
            throw new IllegalArgumentException("Invalid step value: " + step);
        }
        return switch (unit) {
            case 'm' -> value;
            case 'h' -> value * 60;
            case 'd' -> value * 1440;
            default -> throw new IllegalArgumentException(
                "Invalid step unit: " + step + ", expected m/h/d");
        };
    }

    /**
     * 对应 {@code BasicLearningStepsStrategy(params, state, cur_step)}。
     *
     * @return 评分 → 步长信息；空 Map 表示「没有可用步长，走常规间隔」
     */
    static Map<Integer, StepInfo> strategy(FsrsParameters params, int state, int curStep) {
        String[] steps = (state == FsrsState.RELEARNING || state == FsrsState.REVIEW)
            ? params.relearningSteps
            : params.learningSteps;
        int len = steps.length;
        if (len == 0 || curStep >= len) {
            return Map.of();
        }

        String first = steps[0];
        Map<Integer, StepInfo> result = new LinkedHashMap<>();

        if (state == FsrsState.REVIEW) {
            // 复习答错：只排 Again，直接回到重学第一步
            result.put(FsrsState.RATING_AGAIN,
                new StepInfo(toMinutes(steps[Math.max(0, curStep)]), 0));
            return result;
        }

        result.put(FsrsState.RATING_AGAIN, new StepInfo(toMinutes(first), 0));

        int hardMinutes = (len == 1)
            ? (int) Math.round(toMinutes(first) * 1.5)
            : (int) Math.round((toMinutes(first) + toMinutes(steps[1])) / 2.0);
        result.put(FsrsState.RATING_HARD, new StepInfo(hardMinutes, curStep));

        if (curStep + 1 < len) {
            int nextMinutes = toMinutes(steps[curStep + 1]);
            if (nextMinutes != 0) {
                result.put(FsrsState.RATING_GOOD, new StepInfo(nextMinutes, curStep + 1));
            }
        }
        return result;
    }

    /** 复刻 JS {@code parseInt(s, 10)} 的「取前导整数」语义。 */
    private static int parseLeadingInt(String s, String original) {
        int i = 0;
        int n = s.length();
        boolean negative = false;
        if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            negative = s.charAt(i) == '-';
            i++;
        }
        long value = 0;
        boolean any = false;
        while (i < n) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') break;
            value = value * 10 + (c - '0');
            if (value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid step value: " + original);
            }
            any = true;
            i++;
        }
        if (!any) {
            throw new IllegalArgumentException("Invalid step value: " + original);
        }
        return negative ? (int) -value : (int) value;
    }
}
