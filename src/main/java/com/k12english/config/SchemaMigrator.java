package com.k12english.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 老库原地升级：把单用户时代的 {@code cards / logs / settings} 重建成带 {@code user_id} 的多用户结构。
 *
 * <p>为什么需要它：{@code schema.sql} 里全是 {@code CREATE TABLE IF NOT EXISTS}，
 * 表已经存在时整段语句会被跳过，加不上新列、更不可能改主键。
 * 而 {@code cards} 的主键要从 {@code (w)} 变成 {@code (user_id, w)}，SQLite 不支持改主键，
 * 只能重建表。
 *
 * <p>执行时机：在 {@link DataSourceConfig} 里、{@code spring.sql.init} 之前调用。
 * 那时老表还在，先重建；随后 {@code schema.sql} 再补齐 {@code users / invites / sessions}。
 * 新库（一张表都没有）这里什么都不做，交给 {@code schema.sql} 建。
 *
 * <p>老数据统一归到 {@code user_id = ''}，第一个注册的账号（自动成为管理员）
 * 注册时会认领这批数据，见 {@code AuthService.register}。
 *
 * <p><b>注意</b>：下面三段建表语句必须和 {@code schema.sql} 保持一致。
 * 之所以重复一遍而不是读文件，是因为这里要在 sql.init 之前跑，时序上拿不到初始化后的结果。
 */
public final class SchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);

    /** 老数据暂定的归属。第一个注册的账号会把它认领走。 */
    public static final String LEGACY_OWNER = "";

    private static final String CREATE_CARDS = """
            CREATE TABLE cards (
              user_id         TEXT    NOT NULL,
              w               TEXT    NOT NULL,
              due             INTEGER NOT NULL,
              stability       REAL    NOT NULL,
              difficulty      REAL    NOT NULL,
              elapsed_days    INTEGER NOT NULL,
              scheduled_days  INTEGER NOT NULL,
              learning_steps  INTEGER NOT NULL,
              reps            INTEGER NOT NULL,
              lapses          INTEGER NOT NULL,
              state           INTEGER NOT NULL,
              last_review     INTEGER,
              seen            INTEGER NOT NULL,
              wrong           INTEGER NOT NULL,
              first_seen      INTEGER NOT NULL,
              PRIMARY KEY (user_id, w)
            )""";

    private static final String CREATE_LOGS = """
            CREATE TABLE logs (
              id      INTEGER PRIMARY KEY AUTOINCREMENT,
              user_id TEXT    NOT NULL,
              w       TEXT    NOT NULL,
              ts      INTEGER NOT NULL,
              grade   INTEGER NOT NULL,
              mode    TEXT    NOT NULL,
              correct INTEGER NOT NULL
            )""";

    private static final String CREATE_SETTINGS = """
            CREATE TABLE settings (
              user_id TEXT NOT NULL,
              k       TEXT NOT NULL,
              v       TEXT NOT NULL,
              PRIMARY KEY (user_id, k)
            )""";

    private SchemaMigrator() {
    }

    public static void migrate(DataSource ds) {
        try (Connection c = ds.getConnection()) {
            boolean oldCards = lacksUserColumn(c, "cards");
            boolean oldLogs = lacksUserColumn(c, "logs");
            boolean oldSettings = lacksUserColumn(c, "settings");
            if (!oldCards && !oldLogs && !oldSettings) {
                return;
            }

            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                if (oldCards) {
                    st.execute("ALTER TABLE cards RENAME TO cards_legacy");
                    st.execute(CREATE_CARDS);
                    st.execute("""
                            INSERT INTO cards (user_id, w, due, stability, difficulty, elapsed_days,
                                               scheduled_days, learning_steps, reps, lapses, state,
                                               last_review, seen, wrong, first_seen)
                            SELECT '%s', w, due, stability, difficulty, elapsed_days,
                                   scheduled_days, learning_steps, reps, lapses, state,
                                   last_review, seen, wrong, first_seen
                            FROM cards_legacy""".formatted(LEGACY_OWNER));
                    st.execute("DROP TABLE cards_legacy");
                    log.info("已升级 cards 表为多用户结构");
                }
                if (oldLogs) {
                    st.execute("ALTER TABLE logs RENAME TO logs_legacy");
                    st.execute(CREATE_LOGS);
                    st.execute("""
                            INSERT INTO logs (id, user_id, w, ts, grade, mode, correct)
                            SELECT id, '%s', w, ts, grade, mode, correct FROM logs_legacy"""
                            .formatted(LEGACY_OWNER));
                    st.execute("DROP TABLE logs_legacy");
                    log.info("已升级 logs 表为多用户结构");
                }
                if (oldSettings) {
                    st.execute("ALTER TABLE settings RENAME TO settings_legacy");
                    st.execute(CREATE_SETTINGS);
                    st.execute("""
                            INSERT INTO settings (user_id, k, v)
                            SELECT '%s', k, v FROM settings_legacy""".formatted(LEGACY_OWNER));
                    st.execute("DROP TABLE settings_legacy");
                    log.info("已升级 settings 表为多用户结构");
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            // 迁移失败就别硬着头皮启动，否则后面所有查询都会报「no such column: user_id」
            throw new IllegalStateException("进度库升级失败：" + e.getMessage(), e);
        }
    }

    /**
     * 表不存在时返回 false（新库，交给 schema.sql 建）；
     * 表存在但没有 user_id 列时返回 true（老库，需要重建）。
     */
    private static boolean lacksUserColumn(Connection c, String table) throws SQLException {
        List<String> cols = new ArrayList<>();
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                cols.add(rs.getString("name"));
            }
        }
        if (cols.isEmpty()) {
            return false;
        }
        return !cols.contains("user_id");
    }
}
