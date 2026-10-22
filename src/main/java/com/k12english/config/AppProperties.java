package com.k12english.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用配置。全部可用 application.yml 或命令行参数覆盖。
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * 数据目录。SQLite 进度库放在这里，默认 {@code ~/.k12-english}。
     * 放在用户目录而不是安装目录，是为了让 exe 升级时学习进度不丢。
     */
    private String dataDir = Paths.get(System.getProperty("user.home"), ".k12-english").toString();

    /** 监听端口。0 表示随机端口。 */
    private int port = 8080;

    /** 启动后自动打开浏览器。 */
    private boolean openBrowser = true;

    /** 词库数据在 classpath 上的位置（由 maven-resources-plugin 从 tools/out 复制进来）。 */
    private String wordsResource = "data/words.json";

    public Path dataPath() {
        return Paths.get(dataDir);
    }

    public Path databaseFile() {
        return dataPath().resolve("progress.db");
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isOpenBrowser() {
        return openBrowser;
    }

    public void setOpenBrowser(boolean openBrowser) {
        this.openBrowser = openBrowser;
    }

    public String getWordsResource() {
        return wordsResource;
    }

    public void setWordsResource(String wordsResource) {
        this.wordsResource = wordsResource;
    }
}
