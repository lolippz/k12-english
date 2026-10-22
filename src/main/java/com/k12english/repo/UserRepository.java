package com.k12english.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 账号、邀请码、会话的存取。
 *
 * <p>和业务数据（卡片 / 日志 / 设置）分开：那三张表按 {@code user_id} 隔离，
 * 由各仓储从 {@code UserContext} 里取当前用户；这里的三张表本身就是账号体系的一部分，
 * 必须能跨用户查询（管理员要看全部用户）。
 */
@Repository
public class UserRepository {

    public record UserRow(String id, String username, String pwd, String role,
            long createdAt, Long lastLogin, String invitedBy) {
    }

    public record InviteRow(String code, int maxUses, int used,
            long createdAt, String expiresAt, String note, String createdBy) {
    }

    private static final RowMapper<UserRow> USER_MAPPER = (rs, i) -> new UserRow(
            rs.getString("id"),
            rs.getString("username"),
            rs.getString("pwd"),
            rs.getString("role"),
            rs.getLong("created_at"),
            (Long) rs.getObject("last_login"),
            rs.getString("invited_by"));

    private static final RowMapper<InviteRow> INVITE_MAPPER = (rs, i) -> new InviteRow(
            rs.getString("code"),
            rs.getInt("max_uses"),
            rs.getInt("used"),
            rs.getLong("created_at"),
            rs.getString("expires_at"),
            rs.getString("note"),
            rs.getString("created_by"));

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /* ---------------------------------------------------------------- 账号 */

    public long countUsers() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        return n == null ? 0 : n;
    }

    public long countAdmins() {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'admin'", Long.class);
        return n == null ? 0 : n;
    }

    public Optional<UserRow> findByUsername(String username) {
        List<UserRow> rows = jdbc.query(
                "SELECT * FROM users WHERE lower(username) = lower(?)", USER_MAPPER, username);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<UserRow> findById(String id) {
        List<UserRow> rows = jdbc.query("SELECT * FROM users WHERE id = ?", USER_MAPPER, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<UserRow> listUsers() {
        return jdbc.query("SELECT * FROM users ORDER BY created_at ASC", USER_MAPPER);
    }

    public void insert(UserRow u) {
        jdbc.update("""
                INSERT INTO users (id, username, pwd, role, created_at, last_login, invited_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, u.id(), u.username(), u.pwd(), u.role(), u.createdAt(), u.lastLogin(),
                u.invitedBy());
    }

    public void touchLogin(String id, long ts) {
        jdbc.update("UPDATE users SET last_login = ? WHERE id = ?", ts, id);
    }

    public void updatePassword(String id, String pwd) {
        jdbc.update("UPDATE users SET pwd = ? WHERE id = ?", pwd, id);
    }

    public void updateRole(String id, String role) {
        jdbc.update("UPDATE users SET role = ? WHERE id = ?", role, id);
    }

    /** 删账号并清掉他的全部学习数据。 */
    public void deleteUser(String id) {
        jdbc.update("DELETE FROM cards WHERE user_id = ?", id);
        jdbc.update("DELETE FROM logs WHERE user_id = ?", id);
        jdbc.update("DELETE FROM settings WHERE user_id = ?", id);
        jdbc.update("DELETE FROM sessions WHERE user_id = ?", id);
        jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    /**
     * 认领老版本（单用户）数据：把 {@code user_id = ''} 的行全部划给指定账号。
     * 只在第一个账号注册时调用一次，返回认领的卡片数。
     */
    public long claimLegacyData(String userId) {
        jdbc.update("UPDATE cards SET user_id = ? WHERE user_id = ''", userId);
        jdbc.update("UPDATE logs SET user_id = ? WHERE user_id = ''", userId);
        jdbc.update("UPDATE settings SET user_id = ? WHERE user_id = ''", userId);
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cards WHERE user_id = ?", Long.class, userId);
        return n == null ? 0 : n;
    }

    /* ---------------------------------------------------------------- 邀请码 */

    public List<InviteRow> listInvites() {
        return jdbc.query("SELECT * FROM invites ORDER BY created_at DESC", INVITE_MAPPER);
    }

    public Optional<InviteRow> findInvite(String code) {
        List<InviteRow> rows = jdbc.query(
                "SELECT * FROM invites WHERE code = ?", INVITE_MAPPER, code);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void insertInvite(InviteRow inv) {
        jdbc.update("""
                INSERT INTO invites (code, max_uses, used, created_at, expires_at, note, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, inv.code(), inv.maxUses(), inv.used(), inv.createdAt(), inv.expiresAt(),
                inv.note(), inv.createdBy());
    }

    public void consumeInvite(String code) {
        jdbc.update("UPDATE invites SET used = used + 1 WHERE code = ?", code);
    }

    public void deleteInvite(String code) {
        jdbc.update("DELETE FROM invites WHERE code = ?", code);
    }

    /* ---------------------------------------------------------------- 会话 */

    public void createSession(String token, String userId, long createdAt, long expiresAt) {
        jdbc.update("""
                INSERT INTO sessions (token, user_id, created_at, expires_at)
                VALUES (?, ?, ?, ?)
                """, token, userId, createdAt, expiresAt);
    }

    /** 取会话对应的用户 id；不存在或已过期返回 null（顺手删掉过期行）。 */
    public String sessionUser(String token, long now) {
        List<String> rows = jdbc.queryForList(
                "SELECT user_id FROM sessions WHERE token = ? AND expires_at > ?",
                String.class, token, now);
        if (rows.isEmpty()) {
            jdbc.update("DELETE FROM sessions WHERE token = ?", token);
            return null;
        }
        return rows.get(0);
    }

    public void deleteSession(String token) {
        jdbc.update("DELETE FROM sessions WHERE token = ?", token);
    }

    public void purgeExpiredSessions(long now) {
        jdbc.update("DELETE FROM sessions WHERE expires_at <= ?", now);
    }
}
