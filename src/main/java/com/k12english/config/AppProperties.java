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

    /**
     * 访问口令。用于「公网 / 局域网访问时要求输入一次口令」。
     *
     * <ul>
     *   <li>留空（默认）：自动生成 12 位口令，保存在 {@code data-dir/access.token}，重启不变</li>
     *   <li>指定值：直接用这个值</li>
     *   <li>{@code off}：关闭校验（仅建议纯本机使用）</li>
     * </ul>
     *
     * <p>本机 127.0.0.1 直连始终免口令，所以日常使用不会被打断。
     */
    private String accessToken = "";

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

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}
