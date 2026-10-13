package com.k12english.support;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.util.StreamUtils;

/**
 * 测试用 SQLite 环境。
 *
 * <p>每个测试方法一个临时库文件，跑完删掉。用文件而不是 {@code :memory:}，
 * 是因为 SQLite 的内存库是「每连接一份」，连接池一换连接数据就没了。
 */
public final class TestDatabase {

    private TestDatabase() {
    }

    /** 建一个已执行过 schema.sql 的临时库。 */
    public static JdbcTemplate freshJdbc() {
        try {
            Path db = Files.createTempFile("k12-test-", ".db");
            db.toFile().deleteOnExit();
            SimpleDriverDataSource ds = new SimpleDriverDataSource(
                    new org.sqlite.JDBC(),
                    "jdbc:sqlite:" + db.toAbsolutePath().toString().replace('\\', '/'));
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            String schema = StreamUtils.copyToString(
                    new ClassPathResource("schema.sql").getInputStream(), StandardCharsets.UTF_8);
            for (String stmt : schema.split(";")) {
                if (!stmt.isBlank()) {
                    jdbc.execute(stmt);
                }
            }
            return jdbc;
        } catch (Exception e) {
            throw new IllegalStateException("测试库初始化失败", e);
        }
    }
}
