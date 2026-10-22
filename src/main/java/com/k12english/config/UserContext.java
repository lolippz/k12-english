package com.k12english.config;

/**
 * 当前请求所属的用户。由 {@code AuthFilter} 在每个请求开头设置、结束时清除。
 *
 * <p>为什么用 ThreadLocal 而不是给每个仓储方法加一个 {@code userId} 参数：
 * 业务查询里 user_id 只是一个过滤条件，不是业务语义的一部分。加参数会污染
 * service / controller 的全部签名，测试也要跟着改。放在上下文里，仓储内部
 * 自己取，调用方完全无感。
 *
 * <p>代价是隐式依赖：脱离 Web 请求（单元测试、后台任务）时取到的是
 * {@link #ANONYMOUS}。测试里的插入和查询都用同一个值，所以互相自洽。
 */
public final class UserContext {

    /** 没有登录态时使用的用户标识。老版本单用户数据也归在这个值下，等第一个账号认领。 */
    public static final String ANONYMOUS = "";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static String current() {
        String id = CURRENT.get();
        return id == null ? ANONYMOUS : id;
    }

    public static void set(String userId) {
        CURRENT.set(userId == null ? ANONYMOUS : userId);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
