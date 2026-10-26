package com.jade.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jade.common.BizException;
import com.jade.common.CurrentUser;
import com.jade.common.RequirePerm;
import com.jade.common.Result;
import com.jade.entity.OpLog;
import com.jade.entity.Role;
import com.jade.entity.User;
import com.jade.mapper.OpLogMapper;
import com.jade.mapper.RoleMapper;
import com.jade.mapper.UserMapper;
import com.jade.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证与账号管理。对应 specs/000-base/contracts/api.md。
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final OpLogMapper opLogMapper;

    private static final DateTimeFormatter OPLOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AuthController(AuthService authService, UserMapper userMapper, RoleMapper roleMapper,
                          OpLogMapper opLogMapper) {
        this.authService = authService;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.opLogMapper = opLogMapper;
    }

    /** 健康检查，容器与前端探活用 */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "up");
        m.put("app", "jade-server");
        return Result.ok(m);
    }

    public static class LoginReq {
        @NotBlank(message = "用户名不能为空")
        public String username;
        @NotBlank(message = "口令不能为空")
        public String password;
    }

    @PostMapping("/auth/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(authService.login(req.username, req.password));
    }

    @GetMapping("/auth/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(authService.me());
    }

    public static class PwdReq {
        @NotBlank
        public String oldPassword;
        @NotBlank
        public String newPassword;
    }

    @PutMapping("/auth/password")
    public Result<Void> changePassword(@Valid @RequestBody PwdReq req) {
        authService.changePassword(req.oldPassword, req.newPassword);
        return Result.ok();
    }

    // ---------------- 账号管理（仅老板） ----------------

    @GetMapping("/users")
    @RequirePerm("UC-12")
    public Result<Map<String, Object>> listUsers(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String status) {
        QueryWrapper<User> q = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like("username", keyword).or().like("name", keyword));
        }
        if (status != null && !status.isBlank()) {
            q.eq("status", status);
        }
        q.orderByDesc("id");
        List<User> users = userMapper.selectList(q);
        List<Map<String, Object>> items = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("name", u.getName());
            m.put("status", u.getStatus());
            List<Role> rs = authService.rolesOf(u.getId());
            List<Map<String, String>> rl = new ArrayList<>();
            for (Role r : rs) {
                Map<String, String> rm = new LinkedHashMap<>();
                rm.put("code", r.getRoleCode());
                rm.put("name", r.getRoleName());
                rl.add(rm);
            }
            m.put("roles", rl);
            items.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", items.size());
        data.put("items", items);
        return Result.ok(data);
    }

    public static class CreateUserReq {
        @NotBlank
        public String username;
        @NotBlank
        public String name;
        @NotBlank
        public String password;
        public List<Long> roleIds;
    }

    @PostMapping("/users")
    @RequirePerm("UC-12")
    public Result<Map<String, Object>> createUser(@Valid @RequestBody CreateUserReq req) {
        CurrentUser cu = CurrentUser.require();
        Role boss = roleMapper.selectOne(new QueryWrapper<Role>().eq("role_code", "BOSS"));
        if (req.roleIds != null && boss != null && req.roleIds.contains(boss.getId()) && !cu.isBoss()) {
            // 按角色编码找 BOSS，不依赖 seed 的主键顺序
            throw BizException.forbidden("只有老板能开通老板角色");
        }
        return Result.ok(authService.createUser(req.username, req.name, req.password, req.roleIds));
    }

    public static class UpdateUserReq {
        public String name;
        public String status;
        public String password;
    }

    @PutMapping("/users/{id}")
    @RequirePerm("UC-12")
    public Result<Void> updateUser(@PathVariable Long id, @RequestBody UpdateUserReq req) {
        CurrentUser cu = CurrentUser.require();
        User u = userMapper.selectById(id);
        if (u == null) {
            throw BizException.notFound("账号不存在");
        }
        // 不能停用自己，避免无管理员可用（spec FR 边界情况）
        if ("停用".equals(req.status) && id.equals(cu.getUserId())) {
            throw BizException.conflict("不能停用当前登录账号");
        }
        if (req.name != null && !req.name.isBlank()) {
            u.setName(req.name);
        }
        if (req.status != null && !req.status.isBlank()) {
            if (!List.of("启用", "停用").contains(req.status)) {
                throw BizException.badRequest("状态只能是启用或停用");
            }
            u.setStatus(req.status);
        }
        userMapper.updateById(u);
        if (req.password != null && !req.password.isBlank()) {
            authService.resetPassword(id, req.password);
        }
        return Result.ok();
    }

    @GetMapping("/roles")
    @RequirePerm("UC-12")
    public Result<List<Role>> roles() {
        return Result.ok(roleMapper.selectList(new QueryWrapper<Role>().orderByAsc("id")));
    }

    /** 操作日志：登录后的改操作留痕，谁在什么时候对什么做了什么、结果如何 */
    @GetMapping("/op-logs")
    @RequirePerm("UC-12")
    public Result<Map<String, Object>> opLogs(@RequestParam(required = false) String keyword,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100;
        QueryWrapper<OpLog> q = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like("path", keyword).or().like("username", keyword));
        }
        q.orderByDesc("id");
        Page<OpLog> p = opLogMapper.selectPage(new Page<>(page, size), q);
        Map<Long, String> names = new HashMap<>();
        for (User u : userMapper.selectList(null)) names.put(u.getId(), u.getName());
        List<Map<String, Object>> items = new ArrayList<>();
        for (OpLog l : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("time", l.getCreatedAt() == null ? null : l.getCreatedAt().format(OPLOG_TIME));
            m.put("username", l.getUsername());
            m.put("name", l.getUserId() == null ? null : names.get(l.getUserId()));
            m.put("method", l.getMethod());
            m.put("path", l.getPath());
            m.put("status", l.getStatus());
            items.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", p.getTotal());
        data.put("page", page);
        data.put("size", size);
        data.put("items", items);
        return Result.ok(data);
    }
}
