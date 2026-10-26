#!/usr/bin/env bash
# 安全回归：注入、令牌加密、篡改、越权、响应头
# 用法：bash tools/security-test.sh [baseUrl]
set -u
# 服务器用自签证书，脚本内统一跳过证书校验（仅测试用途，不影响线上校验逻辑）
curl() { command curl -sk "$@"; }
B="${1:-https://203.0.113.10:8080}"
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); printf "  PASS  %s\n" "$1"; }
bad()  { FAIL=$((FAIL+1)); printf "  FAIL  %s  → %s\n" "$1" "$2"; }
chk()  { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1" "期望 $3 实得 $2"; fi; }

code() { curl -sk -m 10 -o /dev/null -w '%{http_code}' "$@"; }
# 只带一个请求头打 /api/goods，引号问题一次收口
hdr_code() { curl -sk -m 10 -o /dev/null -w '%{http_code}' -H "$1" "$B/api/goods"; }
json() { curl -sk -m 10 "$@"; }

echo "== 1. 登录与令牌结构 =="
LOGIN=$(json -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}')
TOKEN=$(printf '%s' "$LOGIN" | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
[ -n "${TOKEN:-}" ] && ok "登录成功并拿到令牌" || { bad "登录" "$LOGIN"; exit 1; }

SEG_COUNT=$(printf '%s' "$TOKEN" | awk -F. '{print NF}')
chk "令牌为三段式结构" "$SEG_COUNT" "3"

# 载荷段是否可读出明文声明
PAYLOAD=$(printf '%s' "$TOKEN" | cut -d. -f2)
DECODED=$(printf '%s' "$PAYLOAD" | python -c "
import sys,base64
s=sys.stdin.read().strip()
s+='='*(-len(s)%4)
sys.stdout.write(base64.urlsafe_b64decode(s).decode('utf-8','replace'))
" 2>/dev/null)
case "$DECODED" in
  *admin*|*perms*|*UC-*|*username*) bad "载荷不可读" "解出了明文：$DECODED" ;;
  *) ok "载荷段解不出明文声索（长度 $(printf '%s' "$DECODED" | wc -c) 字节乱码）" ;;
esac

echo "== 2. 令牌可用性 =="
chk "带令牌访问业务接口" "$(code -H "Authorization: Bearer $TOKEN" "$B/api/goods?page=1&size=1")" "200"
chk "不带令牌访问业务接口" "$(code "$B/api/goods?page=1&size=1")" "401"
chk "令牌不带 Bearer 前缀" "$(hdr_code "Authorization: $TOKEN")" "401"

TAMPERED=$(printf '%s' "$TOKEN" | python -c "
import sys
t=sys.stdin.read().strip().split('.')
p=t[1]; i=len(p)//2
t[1]=p[:i]+('A' if p[i]!='A' else 'B')+p[i+1:]
print('.'.join(t))")
chk "篡改载荷一个字符" "$(code -H "Authorization: Bearer $TAMPERED" "$B/api/goods")" "401"
chk "伪造签名" "$(hdr_code "Authorization: Bearer ${TOKEN%.*}.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")" "401"
chk "乱码令牌" "$(hdr_code 'Authorization: Bearer not-a-token')" "401"

# 旧版明文体 JWT（无 d 声明）必须失效
OLD=$(python - "$B" <<'PY'
import base64, hmac, hashlib, json, sys, time
def b64(b): return base64.urlsafe_b64encode(b).decode().rstrip('=')
h = b64(json.dumps({"alg":"HS256"}, separators=(',',':')).encode())
p = b64(json.dumps({"sub":"1","username":"admin","perms":["UC-01"],
                    "exp":int(time.time())+3600}, separators=(',',':')).encode())
sig = b64(hmac.new(b"jade-store-system-demo-secret-key-2026-please-change-me",
                   (h+'.'+p).encode(), hashlib.sha256).digest())
print(h+'.'+p+'.'+sig)
PY
)
chk "旧版明文令牌被拒" "$(code -H "Authorization: Bearer $OLD" "$B/api/goods")" "401"

echo "== 3. 登录注入与边界 =="
trylogin() { curl -s -m 10 -X POST "$B/api/auth/login" -H 'Content-Type: application/json' -d "$1"; }
for payload in \
  '{"username":"'"'"' OR '"'"'1'"'"'='"'"'1","password":"x"}' \
  '{"username":"admin'"'"'--","password":"x"}' \
  '{"username":"admin'"'"' #","password":"x"}' \
  '{"username":"\" OR 1=1 --","password":"x"}' \
  '{"username":"admin\"/*","password":"x"}' ; do
  R=$(trylogin "$payload")
  C=$(printf '%s' "$R" | python -c "import sys,json;print(json.load(sys.stdin).get('code','?'))" 2>/dev/null)
  if [ "$C" = "401" ] || [ "$C" = "400" ]; then
    ok "注入载荷被拒 ($(printf '%s' "$payload" | cut -c1-42)…)"
  else
    bad "注入载荷" "返回 $R"
  fi
done

R=$(trylogin '{"username":"admin","password":"'"'"' OR '"'"'1'"'"'='"'"'1"}')
C=$(printf '%s' "$R" | python -c "import sys,json;print(json.load(sys.stdin).get('code','?'))" 2>/dev/null)
[ "$C" = "401" ] || [ "$C" = "423" ] && ok "口令位注入被拒" || bad "口令位注入" "$R"

LONG=$(python -c "print('a'*500)")
chk "超长用户名" "$(code -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$LONG\",\"password\":\"x\"}")" "401"

chk "口令为空（Bean Validation）" "$(code -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":""}')" "400"

echo "== 4. 响应头 =="
H=$(curl -s -D - -o /dev/null -m 10 -H "Authorization: Bearer $TOKEN" "$B/api/goods?page=1&size=1")
for h in "X-Content-Type-Options: nosniff" "X-Frame-Options: DENY" "Referrer-Policy: no-referrer" "Cache-Control: no-store"; do
  case "$(printf '%s' "$H" | tr 'A-Z' 'a-z')" in
    *"$(printf '%s' "$h" | tr 'A-Z' 'a-z')"*) ok "响应头 $h" ;;
    *) bad "响应头 $h" "缺失" ;;
  esac
done

echo
echo "合计 $((PASS+FAIL)) 项，通过 $PASS，失败 $FAIL"
[ "$FAIL" -eq 0 ]
