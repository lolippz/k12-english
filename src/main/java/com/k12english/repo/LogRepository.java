package com.k12english.repo;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.k12english.domain.ReviewLog;

/** 复习日志存取与学情聚合。 */
@Repository
public class LogRepository {

    private static final RowMapper<ReviewLog> MAPPER = (rs, i) -> new ReviewLog(
            rs.getInt("id"),
            rs.getString("w"),
            rs.getLong("ts"),
            rs.getInt("grade"),
            rs.getString("mode"),
            rs.getInt("correct") != 0);

    private final JdbcTemplate jdbc;

    public LogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 追加一条日志，并回填自增主键。
     *
     * <p>注意：不能先 INSERT 再单独查 {@code last_insert_rowid()}——那个函数是
     * 「连接级」的，在连接池下 SELECT 很可能落到另一条连接上，拿到 0 或别人的值。
     * 用 {@link GeneratedKeyHolder} 把取值和插入绑在同一条语句里。
     */
    public ReviewLog add(ReviewLog log) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO logs (w, ts, grade, mode, correct) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, log.w());
            ps.setLong(2, log.ts());
            ps.setInt(3, log.grade());
            ps.setString(4, log.mode());
            ps.setInt(5, log.correct() ? 1 : 0);
            return ps;
        }, keys);

        Number key = keys.getKey();
        return new ReviewLog(key == null ? null : key.intValue(), log.w(), log.ts(), log.grade(),
                log.mode(), log.correct());
    }

    public void addAll(List<ReviewLog> logs) {
        for (ReviewLog l : logs) {
            add(l);
        }
    }

    /** 按时间倒序取最近若干条。 */
    public List<ReviewLog> recent(int limit) {
        return jdbc.query(
                "SELECT id, w, ts, grade, mode, correct FROM logs ORDER BY ts DESC, id DESC LIMIT ?",
                MAPPER, limit);
    }

    public List<ReviewLog> findAll() {
        return jdbc.query("SELECT id, w, ts, grade, mode, correct FROM logs ORDER BY ts ASC, id ASC",
                MAPPER);
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM logs", Long.class);
        return n == null ? 0 : n;
    }

    /**
     * 按本地日期聚合最近 N 天：返回 [日期 yyyy-MM-dd, 总数, 答对数]。
     *
     * <p>用 SQLite 的 {@code localtime} 转换，保证「今天」是按用户本地时区算的，
     * 而不是 UTC——否则晚上 8 点之后的学习会算到第二天。
     */
    public List<Object[]> dailyCounts(long sinceTs) {
        return jdbc.query(
                "SELECT date(ts / 1000, 'unixepoch', 'localtime') AS d, "
                        + "COUNT(*) AS n, SUM(correct) AS ok "
                        + "FROM logs WHERE ts >= ? GROUP BY d ORDER BY d ASC",
                (rs, i) -> new Object[] { rs.getString("d"), rs.getLong("n"), rs.getLong("ok") },
                sinceTs);
    }

    /** 有学习记录的全部本地日期，按倒序。用于算连续打卡。 */
    public List<String> distinctDaysDesc() {
        return jdbc.queryForList(
                "SELECT DISTINCT date(ts / 1000, 'unixepoch', 'localtime') AS d "
                        + "FROM logs ORDER BY d DESC",
                String.class);
    }

    /** 累计作答数与答对数。 */
    public long[] totalAndCorrect() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM logs", Long.class);
        Long ok = jdbc.queryForObject("SELECT COALESCE(SUM(correct), 0) FROM logs", Long.class);
        return new long[] { n == null ? 0 : n, ok == null ? 0 : ok };
    }

    /** 错得最多的词：[单词, 作答数, 错误数]，按错误数倒序。 */
    public List<Object[]> topWrongWords(int limit) {
        return jdbc.query(
                "SELECT w, COUNT(*) AS n, SUM(CASE WHEN correct = 0 THEN 1 ELSE 0 END) AS wrong "
                        + "FROM logs GROUP BY w HAVING wrong > 0 ORDER BY wrong DESC, n DESC LIMIT ?",
                (rs, i) -> new Object[] { rs.getString("w"), rs.getLong("n"), rs.getLong("wrong") },
                limit);
    }

    public void clear() {
        jdbc.update("DELETE FROM logs");
    }
}
