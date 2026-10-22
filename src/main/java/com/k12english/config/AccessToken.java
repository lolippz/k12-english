package com.k12english.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 访问口令的解析与校验。
 *
 * <p>口令来源优先级：{@code app.access-token} 配置 &gt; 数据目录里的 {@code access.token} 文件
 * &gt; 现场生成并落盘。落盘是为了重启后口令不变，用户不用每次重新输入。
 *
 * <p>字符表刻意去掉了容易看错的 {@code 0 O 1 l I} 和 shell 特殊字符 {@code ! $ % &}，
 * 这样口令既方便口头/手输，也能安全地拼进 {@code share.bat} 和 URL。
 */
@Component
public class AccessToken {

    private static final Logger log = LoggerFactory.getLogger(AccessToken.class);

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final String FILE_NAME = "access.token";
    private static final int LENGTH = 12;

    private final boolean enabled;
    private final String value;
    private final Path file;

    public AccessToken(AppProperties props) {
        this.file = props.dataPath().resolve(FILE_NAME);

        String configured = props.getAccessToken();
        if (configured != null) {
            String c = configured.trim();
            if (c.equalsIgnoreCase("off") || c.equalsIgnoreCase("false") || c.equalsIgnoreCase("none")) {
                this.enabled = false;
                this.value = "";
                log.warn("访问口令校验已关闭（app.access-token={}）。公网暴露时任何人都能改写学习记录。", c);
                return;
            }
            if (!c.isEmpty()) {
                this.enabled = true;
                this.value = c;
                return;
            }
        }

        this.enabled = true;
        this.value = loadOrCreate();
    }

    private String loadOrCreate() {
        try {
            Files.createDirectories(file.getParent());
            if (Files.isReadable(file)) {
                String existing = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (existing.length() >= 8) {
                    return existing;
                }
            }
            String generated = randomToken();
            Files.writeString(file, generated + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("已生成访问口令并保存到 {}", file.toAbsolutePath());
            return generated;
        } catch (IOException e) {
            String generated = randomToken();
            log.warn("口令文件不可读写（{}），本次启动使用临时口令，重启后会变", file.toAbsolutePath(), e);
            return generated;
        }
    }

    private static String randomToken() {
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** 是否启用口令校验。 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 当前生效的口令（未启用时为空串）。 */
    public String value() {
        return value;
    }

    /** 口令文件位置，便于日志提示用户去哪里找。 */
    public Path file() {
        return file;
    }

    /**
     * 定长时间比较，避免通过响应时间逐字符猜口令。
     * 口令为空（未启用）时一律返回 false —— 未启用的情况由调用方提前短路。
     */
    public boolean matches(String candidate) {
        if (!enabled || candidate == null) {
            return false;
        }
        byte[] a = value.getBytes(StandardCharsets.UTF_8);
        byte[] b = candidate.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
