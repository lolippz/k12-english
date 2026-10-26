package com.k12english.fsrs;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 日期工具，对齐 ts-fsrs 的 {@code date_scheduler} / {@code dateDiffInDays} /
 * {@code date_diff}。
 */
final class FsrsDateUtils {

    private static final long MINUTE_MS = 60L * 1000L;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private FsrsDateUtils() {
    }

    /** 按天或按分钟推进时间。对应 ts-fsrs 的 {@code date_scheduler(now, t, isDay)}。 */
    static Date schedule(Date now, int t, boolean isDay) {
        double delta = isDay ? t * (double) DAY_MS : t * (double) MINUTE_MS;
        return new Date(now.getTime() + (long) delta);
    }

    /**
     * 按 <b>UTC 日历日</b> 求差值，对应 {@code dateDiffInDays}。
     *
     * <p>注意这不是「毫秒差除以 86400000」——跨夏令时或不满 24 小时的跨日
     * 都会被算成 1 天，这正是 FSRS 想要的口径。
     */
    static int diffInCalendarDays(Date last, Date cur) {
        LocalDate a = Instant.ofEpochMilli(last.getTime()).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate b = Instant.ofEpochMilli(cur.getTime()).atZone(ZoneOffset.UTC).toLocalDate();
        return (int) ChronoUnit.DAYS.between(a, b);
    }

    /** 毫秒差整除天数（向下取整），对应 {@code date_diff(now, pre, "days")}。 */
    static long diffInDaysFloor(Date now, Date pre) {
        return (long) Math.floor((now.getTime() - pre.getTime()) / (double) DAY_MS);
    }
}
