package com.k12english.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.k12english.auth.Auth;
import com.k12english.repo.UserRepository;
import com.k12english.repo.UserRepository.InviteRow;
import com.k12english.repo.UserRepository.UserRow;

/**
 * 账号体系的业务规则：注册、登录、会话、以及管理员侧的账号与邀请码管理。
 *
 * <p>规则对齐 fund-tracker：
 * <ul>
 *   <li>注册必须持邀请码；<b>第一个注册的账号免邀请码并自动成为管理员</b>
 *       —— 否则新库里还没有任何邀请码，谁都进不去</li>
 *   <li>密码 PBKDF2-HMAC-SHA256 加盐，库里没有明文</li>
 *   <li>会话 7 天，存数据库，重启后仍然有效</li>
 *   <li>同一 IP 连续失败 5 次锁定 15 分钟</li>
 *   <li>不允许删掉/降级最后一个管理员</li>
 * </ul>
 */
@Service
public class AuthService {

    /** 会话 Cookie 名。 */
    public static final String COOKIE = "k12_session";

    private static final long SESSION_TTL_MS = Duration.ofDays(7).toMillis();

    /** 用户名：2–20 位，允许中英文、数字、下划线、点、连字符。 */
    private static final Pattern USERNAME = Pattern.compile("^[\\w.\\-\\u4e00-\\u9fa5]{2,20}$");

    private final UserRepository users;

    public AuthService(UserRepository users) {
        this.users = users;
    }

    /** 登录态里的用户信息，不含密码。 */
    public record SessionUser(String id, String username, String role) {
        public boolean admin() {
            return "admin".equals(role);
        }
    }

    /** 注册 / 登录的结果。{@code error} 非空表示失败。 */
    public record AuthResult(String token, SessionUser user, String error) {
        public boolean ok() {
            return error == null;
        }

        static AuthResult fail(String msg) {
            return new AuthResult(null, null, msg);
        }
    }

    /* ---------------------------------------------------------------- 注册 */

    public AuthResult register(String username, String password, String invite, String clientIp) {
        String name = username == null ? "" : username.trim();
        String problem = validateNameAndPassword(name, password);
        if (problem != null) {
            return AuthResult.fail(problem);
        }

        boolean first = users.countUsers() == 0;
        if (!first) {
            String inviteProblem = inviteProblem(invite);
            if (inviteProblem != null) {
                return AuthResult.fail(inviteProblem);
            }
        }

        if (users.findByUsername(name).isPresent()) {
            return AuthResult.fail("用户名已被占用");
        }

        long now = System.currentTimeMillis();
        UserRow u = new UserRow(
                Auth.newUserId(),
                name,
                Auth.hash(password),
                first ? "admin" : "user",
                now,
                null,
                first ? "" : invite.trim().toUpperCase());

        users.insert(u);

        if (!first) {
            users.consumeInvite(invite.trim().toUpperCase());
        } else {
            // 第一个账号认领老版本（单用户）留下的学习数据
            long claimed = users.claimLegacyData(u.id());
            if (claimed > 0) {
                org.slf4j.LoggerFactory.getLogger(AuthService.class)
                        .info("第一个账号 {} 认领了 {} 张老卡片", name, claimed);
            }
        }

        return new AuthResult(newSession(u.id(), now),
                new SessionUser(u.id(), u.username(), u.role()), null);
    }

    private String validateNameAndPassword(String name, String password) {
        if (!USERNAME.matcher(name).matches()) {
            return "用户名需 2–20 位，可用中英文、数字、下划线、点、连字符";
        }
        if (password == null || password.length() < 6) {
            return "密码至少 6 位";
        }
        if (password.length() > 72) {
            return "密码过长（最多 72 位）";
        }
        return null;
    }

    private String inviteProblem(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "请填写邀请码";
        }
        Optional<InviteRow> found = users.findInvite(code.trim().toUpperCase());
        if (found.isEmpty()) {
            return "邀请码不存在";
        }
        InviteRow inv = found.get();
        if (inv.used() >= inv.maxUses()) {
            return "邀请码已用完";
        }
        if (inv.expiresAt() != null && !inv.expiresAt().isBlank()) {
            try {
                if (LocalDate.parse(inv.expiresAt()).isBefore(LocalDate.now())) {
                    return "邀请码已过期";
                }
            } catch (DateTimeParseException ignored) {
                // 日期格式异常时不阻断，按长期有效处理
            }
        }
        return null;
    }

    /* ---------------------------------------------------------------- 登录 */

    public AuthResult login(String username, String password, String clientIp) {
        long locked = Auth.lockedFor(clientIp);
        if (locked > 0) {
            return AuthResult.fail("尝试次数过多，请 " + locked + " 秒后再试");
        }

        String name = username == null ? "" : username.trim();
        Optional<UserRow> found = users.findByUsername(name);
        if (found.isEmpty() || !Auth.verify(password, found.get().pwd())) {
            Auth.recordFailure(clientIp);
            Auth.purgeAttempts();
            // 不区分「用户不存在」和「密码错」，避免探测账号是否存在
            return AuthResult.fail("用户名或密码不正确");
        }

        Auth.recordSuccess(clientIp);
        UserRow u = found.get();
        long now = System.currentTimeMillis();
        users.touchLogin(u.id(), now);
        users.purgeExpiredSessions(now);

        return new AuthResult(newSession(u.id(), now),
                new SessionUser(u.id(), u.username(), u.role()), null);
    }

    private String newSession(String userId, long now) {
        String token = Auth.newToken();
        users.createSession(token, userId, now, now + SESSION_TTL_MS);
        return token;
    }

    /** 用 Cookie 里的令牌换用户；令牌无效或过期返回 empty。 */
    public Optional<SessionUser> resolve(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        String userId = users.sessionUser(token, System.currentTimeMillis());
        if (userId == null) {
            return Optional.empty();
        }
        return users.findById(userId)
                .map(u -> new SessionUser(u.id(), u.username(), u.role()));
    }

    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            users.deleteSession(token);
        }
    }

    /* ---------------------------------------------------------------- 管理员 */

    public List<UserRow> listUsers() {
        return users.listUsers();
    }

    public List<InviteRow> listInvites() {
        return users.listInvites();
    }

    /** 生成邀请码。返回新码，出错时抛 IllegalArgumentException（由统一异常处理转 400）。 */
    public InviteRow createInvite(int maxUses, String expiresAt, String note, String createdBy) {
        int uses = Math.max(1, Math.min(999, maxUses));
        String exp = (expiresAt == null || expiresAt.isBlank()) ? null : expiresAt.trim();
        if (exp != null) {
            try {
                LocalDate.parse(exp);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("过期日期格式应为 yyyy-MM-dd");
            }
        }
        String code;
        do {
            code = randomCode();
        } while (users.findInvite(code).isPresent());

        InviteRow inv = new InviteRow(code, uses, 0, System.currentTimeMillis(), exp,
                note == null ? "" : note.trim(), createdBy);
        users.insertInvite(inv);
        return inv;
    }

    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static String randomCode() {
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CODE_CHARS[rnd.nextInt(CODE_CHARS.length)]);
        }
        return sb.toString();
    }

    public void deleteInvite(String code) {
        users.deleteInvite(code);
    }

    /** 删除账号。返回错误文案，成功返回 null。 */
    public String deleteUser(String targetId, String actorId) {
        if (targetId == null || targetId.equals(actorId)) {
            return "不能删除自己";
        }
        Optional<UserRow> target = users.findById(targetId);
        if (target.isEmpty()) {
            return "用户不存在";
        }
        if ("admin".equals(target.get().role()) && users.countAdmins() <= 1) {
            return "至少要保留一个管理员";
        }
        users.deleteUser(targetId);
        return null;
    }

    /** 改角色。返回错误文案，成功返回 null。 */
    public String setRole(String targetId, String role, String actorId) {
        if (!"admin".equals(role) && !"user".equals(role)) {
            return "角色只能是 admin 或 user";
        }
        Optional<UserRow> target = users.findById(targetId);
        if (target.isEmpty()) {
            return "用户不存在";
        }
        if ("admin".equals(target.get().role()) && !"admin".equals(role)
                && users.countAdmins() <= 1) {
            return "至少要保留一个管理员";
        }
        users.updateRole(targetId, role);
        return null;
    }

    /** 重置密码（管理员操作）。返回错误文案，成功返回 null。 */
    public String resetPassword(String targetId, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            return "新密码至少 6 位";
        }
        if (newPassword.length() > 72) {
            return "新密码过长（最多 72 位）";
        }
        if (users.findById(targetId).isEmpty()) {
            return "用户不存在";
        }
        users.updatePassword(targetId, Auth.hash(newPassword));
        return null;
    }
}
