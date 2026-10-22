package com.k12english.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.k12english.config.UserContext;
import com.k12english.repo.UserRepository.InviteRow;
import com.k12english.repo.UserRepository.UserRow;
import com.k12english.service.AuthService;

/**
 * 管理员后台：账号与邀请码。
 *
 * <p>访问控制在 {@link AuthFilter} 里做（{@code /api/admin/**} 要求 role = admin），
 * 这里只负责业务。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuthService auth;

    public AdminController(AuthService auth) {
        this.auth = auth;
    }

    public record CreateInviteRequest(int maxUses, String expiresAt, String note) {
    }

    public record RoleRequest(String role) {
    }

    public record PasswordRequest(String password) {
    }

    /* ---------------------------------------------------------------- 账号 */

    @GetMapping("/users")
    public Map<String, Object> users() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (UserRow u : auth.listUsers()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.id());
            m.put("username", u.username());
            m.put("role", u.role());
            m.put("createdAt", u.createdAt());
            m.put("lastLogin", u.lastLogin());
            m.put("invitedBy", u.invitedBy());
            list.add(m);
        }
        return Map.of("users", list);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable String id) {
        String err = auth.deleteUser(id, UserContext.current());
        return err == null
                ? ResponseEntity.ok(Map.of("ok", true))
                : ResponseEntity.badRequest().body(Map.of("error", err));
    }

    @PostMapping("/users/{id}/role")
    public ResponseEntity<Map<String, Object>> setRole(@PathVariable String id,
            @RequestBody RoleRequest req) {
        String err = auth.setRole(id, req.role(), UserContext.current());
        return err == null
                ? ResponseEntity.ok(Map.of("ok", true))
                : ResponseEntity.badRequest().body(Map.of("error", err));
    }

    @PostMapping("/users/{id}/password")
    public ResponseEntity<Map<String, Object>> resetPassword(@PathVariable String id,
            @RequestBody PasswordRequest req) {
        String err = auth.resetPassword(id, req.password());
        return err == null
                ? ResponseEntity.ok(Map.of("ok", true))
                : ResponseEntity.badRequest().body(Map.of("error", err));
    }

    /* ---------------------------------------------------------------- 邀请码 */

    @GetMapping("/invites")
    public Map<String, Object> invites() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (InviteRow i : auth.listInvites()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", i.code());
            m.put("maxUses", i.maxUses());
            m.put("used", i.used());
            m.put("createdAt", i.createdAt());
            m.put("expiresAt", i.expiresAt());
            m.put("note", i.note());
            list.add(m);
        }
        return Map.of("invites", list);
    }

    @PostMapping("/invites")
    public ResponseEntity<Map<String, Object>> createInvite(@RequestBody CreateInviteRequest req) {
        try {
            InviteRow inv = auth.createInvite(req.maxUses(), req.expiresAt(), req.note(),
                    UserContext.current());
            return ResponseEntity.ok(Map.of(
                    "code", inv.code(),
                    "maxUses", inv.maxUses(),
                    "used", inv.used(),
                    "expiresAt", inv.expiresAt() == null ? "" : inv.expiresAt(),
                    "note", inv.note()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/invites/{code}")
    public ResponseEntity<Map<String, Object>> deleteInvite(@PathVariable String code) {
        auth.deleteInvite(code);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
