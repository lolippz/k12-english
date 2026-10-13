package com.k12english.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * SQLite 数据源。
 *
 * <p>单机应用用文件型数据库最合适：免安装、免服务、拷贝即备份。
 *
 * <p>两个关键设置：
 * <ul>
 *   <li>{@code journal_mode=WAL} —— 允许「多读一写」并发。默认的 delete 日志模式下
 *       读写互斥，容易撞上 SQLITE_BUSY。</li>
 *   <li>{@code busy_timeout=5000} —— 拿不到锁时等 5 秒再放弃，而不是立刻报错。</li>
 * </ul>
 */
@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    public DataSource dataSource(AppProperties props) throws IOException {
        Path dbFile = props.databaseFile();
        Files.createDirectories(dbFile.getParent());
        log.info("进度库位置: {}", dbFile.toAbsolutePath());

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.toAbsolutePath().toString().replace('\\', '/'));
        cfg.setDriverClassName("org.sqlite.JDBC");
        // 写操作串行由 SQLite 自己保证，池子开小一点更省心；WAL 下读不阻塞写
        cfg.setMaximumPoolSize(4);
        cfg.setMinimumIdle(1);
        cfg.setPoolName("sqlite-pool");
        cfg.addDataSourceProperty("journal_mode", "WAL");
        cfg.addDataSourceProperty("busy_timeout", "5000");
        cfg.addDataSourceProperty("foreign_keys", "true");
        return new HikariDataSource(cfg);
    }
}
