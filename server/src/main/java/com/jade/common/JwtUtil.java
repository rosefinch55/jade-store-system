package com.jade.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ClaimsBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 令牌签发与校验：先加密、再签名。
 *
 * <p>载荷里放的是 AES-256-GCM 密文（键名 d），不是可直接 base64 解开的 JSON。
 * 即便令牌在链路上被截获，也读不出 userId、用户名、角色与权限列表；
 * 签名段保证密文不可篡改，标准 exp 声明由 JJWT 负责过期判定。
 *
 * <p>注意：这不能替代 HTTPS。应用层加密防的是「令牌内容被解出」，
 * 防不了中间人整串重放，传输层仍须 TLS。
 */
@Component
public class JwtUtil {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    /** 密文放在这个声明里，避免与标准声明（sub/exp/iat）混在一起被直接读取 */
    private static final String ENC_CLAIM = "d";

    private final SecretKey signKey;
    private final SecretKey encKey;
    private final long expireMillis;
    private final ObjectMapper json = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    public JwtUtil(@Value("${jade.jwt.secret}") String secret,
                   @Value("${jade.jwt.enc-secret:}") String encSecret,
                   @Value("${jade.jwt.expire-hours:8}") int hours) {
        this.signKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        // 加密密钥单独配置；未配置时用签名密钥派生，避免两处共用一个原始值
        String raw = (encSecret == null || encSecret.isBlank()) ? secret + "|aes-256-gcm" : encSecret;
        this.encKey = new SecretKeySpec(sha256(raw), "AES");
        this.expireMillis = hours * 3600_000L;
    }

    /** 签发令牌：敏感声明全部进密文 */
    public String sign(Long userId, String username, List<String> roles, List<String> perms) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sub", String.valueOf(userId));
        body.put("username", username);
        body.put("roles", roles);
        body.put("perms", perms);

        Date now = new Date();
        return Jwts.builder()
                .claim(ENC_CLAIM, encrypt(writeJson(body)))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(signKey)
                .compact();
    }

    /**
     * 校验并解出声明。签名不对、过期、密文损坏、密钥不匹配，一律返回 null，
     * 调用方按「未登录」处理，不区分失败原因（避免给攻击者探测信息）。
     */
    public Claims parse(String token) {
        try {
            Claims outer = Jwts.parser().verifyWith(signKey).build()
                    .parseSignedClaims(token).getPayload();
            Object enc = outer.get(ENC_CLAIM);
            if (!(enc instanceof String cipherText)) {
                return null;
            }
            Map<String, Object> body = readJson(decrypt(cipherText));
            if (body == null) {
                return null;
            }
            // 外层已验证过签名与过期，这里把 exp 带回来，让调用方继续用标准声明
            ClaimsBuilder cb = Jwts.claims();
            for (Map.Entry<String, Object> e : body.entrySet()) {
                cb.add(e.getKey(), e.getValue());
            }
            cb.expiration(outer.getExpiration());
            return cb.build();
        } catch (Exception e) {
            // 签名错、过期、密文损坏、密钥不匹配统一按未登录处理
            return null;
        }
    }

    public long getExpireSeconds() {
        return expireMillis / 1000;
    }

    // ---------------- 内部：加解密与序列化 ----------------

    private String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance(CIPHER);
            c.init(Cipher.ENCRYPT_MODE, encKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("令牌加密失败", e);
        }
    }

    private String decrypt(String encoded) throws Exception {
        byte[] all = Base64.getUrlDecoder().decode(encoded);
        if (all.length <= IV_BYTES) {
            throw new IllegalArgumentException("密文长度不足");
        }
        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(all, 0, iv, 0, IV_BYTES);
        Cipher c = Cipher.getInstance(CIPHER);
        c.init(Cipher.DECRYPT_MODE, encKey, new GCMParameterSpec(TAG_BITS, iv));
        byte[] plain = c.doFinal(all, IV_BYTES, all.length - IV_BYTES);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private String writeJson(Map<String, Object> body) {
        try {
            return json.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("令牌序列化失败", e);
        }
    }

    private Map<String, Object> readJson(String s) {
        try {
            return json.readValue(s, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("密钥派生失败", e);
        }
    }
}
