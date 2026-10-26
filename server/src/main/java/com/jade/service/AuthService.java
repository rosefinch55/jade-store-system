package com.jade.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jade.common.BizException;
import com.jade.common.CurrentUser;
import com.jade.common.JwtUtil;
import com.jade.entity.Permission;
import com.jade.entity.Role;
import com.jade.entity.RolePermission;
import com.jade.entity.User;
import com.jade.entity.UserRole;
import com.jade.mapper.PermissionMapper;
import com.jade.mapper.RoleMapper;
import com.jade.mapper.RolePermissionMapper;
import com.jade.mapper.UserMapper;
import com.jade.mapper.UserRoleMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务：登录校验、JWT 签发、改密。
 * 登录失败计数放内存，单机够用，重启清零——见 specs/000-base/data-model.md。
 */
@Service
public class AuthService {

    private static final int MAX_FAIL = 5;
    private static final long LOCK_MINUTES = 10;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermMapper;
    private final JwtUtil jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** username -> [失败次数, 锁定截止时间] */
    private final Map<String, Object[]> failCache = new ConcurrentHashMap<>();

    public AuthService(UserMapper userMapper, RoleMapper roleMapper, PermissionMapper permMapper,
                       UserRoleMapper userRoleMapper, RolePermissionMapper rolePermMapper, JwtUtil jwt) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permMapper = permMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermMapper = rolePermMapper;
        this.jwt = jwt;
    }

    /** 登录：校验口令与状态，返回 token 与用户信息 */
    public Map<String, Object> login(String username, String password) {
        // 输入边界：超长与控制字符直接按登录失败处理，不暴露具体原因。
        // SQL 注入由 MyBatis-Plus 的条件构造器统一参数化兜住，这里做的是入参收敛。
        if (username == null || username.isEmpty() || username.length() > 32
                || !isPrintable(username)) {
            throw BizException.unauthorized("用户名或口令错误");
        }
        if (password == null || password.isEmpty() || password.length() > 128) {
            throw BizException.unauthorized("用户名或口令错误");
        }
        checkLock(username);

        User u = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (u == null) {
            recordFail(username);
            throw BizException.unauthorized("用户名或口令错误");
        }
        if (!encoder.matches(password, u.getPasswordHash())) {
            boolean locked = recordFail(username);
            if (locked) {
                throw new BizException(423, "账号已锁定，请 10 分钟后重试或联系老板解锁");
            }
            throw BizException.unauthorized("用户名或口令错误");
        }
        failCache.remove(username);

        // 停用判断放在验密之后：口令错了统一报「用户名或口令错误」，
        // 只有口令正确才暴露「账号已停用」，既不泄露账号存在性，也不放行停用账号
        if (!"启用".equals(u.getStatus())) {
            throw BizException.forbidden("账号已停用");
        }

        List<Role> roles = rolesOf(u.getId());
        List<String> roleCodes = new ArrayList<>();
        for (Role r : roles) {
            roleCodes.add(r.getRoleCode());
        }
        List<String> perms = permsOf(roles);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", u.getId());
        user.put("username", u.getUsername());
        user.put("name", u.getName());
        user.put("roles", roleList(roles));
        user.put("permissions", perms);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", jwt.sign(u.getId(), u.getUsername(), roleCodes, perms));
        data.put("expiresIn", jwt.getExpireSeconds());
        data.put("user", user);
        return data;
    }

    /** 只允许可打印字符，挡掉换行、制表、NUL 等用于构造畸形输入的控制字符 */
    private boolean isPrintable(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < 0x20 || ch == 0x7f) return false;
        }
        return true;
    }

    /** 当前用户信息，结构与 login 的 user 一致 */
    public Map<String, Object> me() {
        CurrentUser cu = CurrentUser.require();
        User u = userMapper.selectById(cu.getUserId());
        if (u == null) {
            throw BizException.unauthorized("用户不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("name", u.getName());
        m.put("roles", roleList(rolesOf(u.getId())));
        m.put("permissions", cu.getPerms());
        return m;
    }

    /** 改自己的口令 */
    public void changePassword(String oldPwd, String newPwd) {
        CurrentUser cu = CurrentUser.require();
        User u = userMapper.selectById(cu.getUserId());
        if (u == null || !encoder.matches(oldPwd, u.getPasswordHash())) {
            throw BizException.badRequest("原口令不正确");
        }
        if (newPwd == null || newPwd.length() < 8
                || !newPwd.matches(".*[A-Za-z].*") || !newPwd.matches(".*\\d.*")) {
            throw BizException.badRequest("新口令至少 8 位，且含字母与数字");
        }
        u.setPasswordHash(encoder.encode(newPwd));
        userMapper.updateById(u);
    }

    public List<Role> rolesOf(Long userId) {
        List<UserRole> urs = userRoleMapper.selectList(new QueryWrapper<UserRole>().eq("user_id", userId));
        if (urs.isEmpty()) {
            return List.of();
        }
        List<Long> ids = urs.stream().map(UserRole::getRoleId).toList();
        return roleMapper.selectBatchIds(ids);
    }

    public List<String> permsOf(List<Role> roles) {
        if (roles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = roles.stream().map(Role::getId).toList();
        List<RolePermission> rps = rolePermMapper.selectList(
                new QueryWrapper<RolePermission>().in("role_id", roleIds));
        if (rps.isEmpty()) {
            return List.of();
        }
        List<Long> pids = rps.stream().map(RolePermission::getPermissionId).distinct().toList();
        return permMapper.selectBatchIds(pids).stream().map(Permission::getPermCode).distinct().toList();
    }

    private List<Map<String, String>> roleList(List<Role> roles) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Role r : roles) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", r.getRoleCode());
            m.put("name", r.getRoleName());
            out.add(m);
        }
        return out;
    }

    private void checkLock(String username) {
        Object[] st = failCache.get(username);
        if (st == null) {
            return;
        }
        LocalDateTime until = (LocalDateTime) st[1];
        if (until != null && LocalDateTime.now().isBefore(until)) {
            long mins = java.time.Duration.between(LocalDateTime.now(), until).toMinutes() + 1;
            throw new BizException(423, "账号已锁定，请 " + mins + " 分钟后重试");
        }
        if (until != null) {
            failCache.remove(username);
        }
    }

    /** 记一次失败，返回是否刚触发锁定 */
    private boolean recordFail(String username) {
        Object[] st = failCache.computeIfAbsent(username, k -> new Object[]{0, null});
        synchronized (st) {
            int n = (Integer) st[0] + 1;
            st[0] = n;
            if (n >= MAX_FAIL) {
                st[1] = LocalDateTime.now().plusMinutes(LOCK_MINUTES);
                return true;
            }
            return false;
        }
    }

    /** 老板重置他人口令 */
    public void resetPassword(Long userId, String newPwd) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw BizException.notFound("账号不存在");
        }
        if (newPwd == null || newPwd.length() < 8
                || !newPwd.matches(".*[A-Za-z].*") || !newPwd.matches(".*\\d.*")) {
            // 重置通道与自助改密同一强度口径，否则这里能给任意账号写进弱口令
            throw BizException.badRequest("新口令至少 8 位，且含字母与数字");
        }
        u.setPasswordHash(encoder.encode(newPwd));
        userMapper.updateById(u);
        // 接手说明里「老板重置口令即解锁」是唯一的解锁路径，不清失败计数就解不开
        failCache.remove(u.getUsername());
    }

    public String encode(String raw) {
        return encoder.encode(raw);
    }

    /** 给外部账号开通时用 */
    public Map<String, Object> createUser(String username, String name, String rawPwd, List<Long> roleIds) {
        Long cnt = userMapper.selectCount(new QueryWrapper<User>().eq("username", username));
        if (cnt != null && cnt > 0) {
            throw BizException.conflict("用户名已存在");
        }
        if (roleIds == null || roleIds.isEmpty()) {
            throw BizException.badRequest("至少选择一个角色");
        }
        if (username == null || !username.matches("[A-Za-z0-9_]{3,32}")) {
            // 与登录入口的边界一致：建出的账号必须能登录
            throw BizException.badRequest("用户名须为 3-32 位字母数字下划线");
        }
        User u = new User();
        u.setUsername(username);
        u.setName(name);
        u.setPasswordHash(encoder.encode(rawPwd));
        u.setStatus("启用");
        userMapper.insert(u);
        for (Long rid : roleIds) {
            UserRole ur = new UserRole();
            ur.setUserId(u.getId());
            ur.setRoleId(rid);
            userRoleMapper.insert(ur);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("status", u.getStatus());
        return m;
    }
}
