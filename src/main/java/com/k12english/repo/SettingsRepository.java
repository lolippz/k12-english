package com.k12english.repo;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.ObjectMapper;
import com.k12english.config.UserContext;
import com.k12english.domain.Settings;

/** 设置存取。每个用户一行，整体序列化成一段 JSON。 */
@Repository
public class SettingsRepository {

    private static final Logger log = LoggerFactory.getLogger(SettingsRepository.class);
    private static final String KEY = "settings";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public SettingsRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Settings load() {
        List<String> rows = jdbc.queryForList(
                "SELECT v FROM settings WHERE user_id = ? AND k = ?",
                String.class, UserContext.current(), KEY);
        if (rows.isEmpty()) {
            return Settings.defaults();
        }
        try {
            return json.readValue(rows.get(0), Settings.class);
        } catch (Exception e) {
            // 数据损坏时退回默认值而不是让应用起不来——单机自用，可用性优先
            log.warn("设置反序列化失败，已回退默认值: {}", e.getMessage());
            return Settings.defaults();
        }
    }

    public Settings save(Settings s) {
        try {
            String v = json.writeValueAsString(s);
            jdbc.update("""
                    INSERT INTO settings (user_id, k, v) VALUES (?, ?, ?)
                    ON CONFLICT(user_id, k) DO UPDATE SET v = excluded.v
                    """, UserContext.current(), KEY, v);
        } catch (Exception e) {
            throw new IllegalStateException("设置序列化失败", e);
        }
        return s;
    }

    public void clear() {
        jdbc.update("DELETE FROM settings WHERE user_id = ?", UserContext.current());
    }
}
