package com.k12english.auth;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 密码哈希与登录限流。只用 JDK 自带的 JCE，不引第三方依赖。
 *
 * <p>密码存储格式：{@code pbkdf2_sha256$迭代次数$盐(Base64)$派生密钥(Base64)}。
 * 每个用户独立随机盐，同一密码在库里也长得不一样；迭代次数写进字符串，
 * 将来调高成本参数时老用户仍能正常登录。
 *
 * <p>会话本身存在数据库里（重启后仍然有效），这里只负责生成随机令牌。
 */
public final class Auth {

    private static final String ALGO = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 同一来源连续失败 5 次，锁定 15 分钟。 */
    private static final int MAX_FAILS = 5;
    private static final long LOCK_MS = 15L * 60 * 1000;

    private static final Map<String, Attempt> ATTEMPTS = new ConcurrentHashMap<>();

    private static final class Attempt {
        int fails;
        long lockedUntil;
    }

    private Auth() {
    }

    /* ---------------------------------------------------------------- 密码 */

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = pbkdf2(password, salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2_sha256$" + ITERATIONS + "$" + b64.encodeToString(salt)
                + "$" + b64.encodeToString(key);
    }

    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !parts[0].equals("pbkdf2_sha256")) {
            return false;
        }
        try {
            int iter = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password, salt, iter);
            // 定长比较，避免按字节提前返回泄露信息
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGO).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("密码哈希失败", e);
        } finally {
            spec.clearPassword();
        }
    }

    /* ---------------------------------------------------------------- 令牌 */

    /** 32 字节随机数，Base64URL 编码后 43 个字符。 */
    public static String newToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /** 用户 id：{@code u} + 毫秒时间戳 + 4 位随机数，可读且基本不会撞。 */
    public static String newUserId() {
        StringBuilder sb = new StringBuilder("u").append(System.currentTimeMillis());
        for (int i = 0; i < 4; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /* ---------------------------------------------------------------- 限流 */

    /** 返回剩余锁定秒数，0 表示未锁定。 */
    public static long lockedFor(String key) {
        Attempt a = ATTEMPTS.get(key);
        if (a == null) {
            return 0;
        }
        long left = a.lockedUntil - System.currentTimeMillis();
        return left > 0 ? (left + 999) / 1000 : 0;
    }

    public static void recordFailure(String key) {
        Attempt a = ATTEMPTS.computeIfAbsent(key, k -> new Attempt());
        synchronized (a) {
            a.fails++;
            if (a.fails >= MAX_FAILS) {
                a.lockedUntil = System.currentTimeMillis() + LOCK_MS;
                a.fails = 0;
            }
        }
    }

    public static void recordSuccess(String key) {
        ATTEMPTS.remove(key);
    }

    /** 顺手清理过期的限流记录，避免表无限增长。 */
    public static void purgeAttempts() {
        long now = System.currentTimeMillis();
        ATTEMPTS.entrySet().removeIf(e -> {
            Attempt a = e.getValue();
            return a.lockedUntil < now && a.fails == 0;
        });
    }
}
