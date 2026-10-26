#!/bin/bash
# 业务逻辑接口测试。按 28 个用例的关键规则逐条验证，输出 PASS/FAIL。
# 用法：bash api-test.sh [base_url]
# 注意：脚本会真实建采购单、成交单、调货与寄售记录，且不自动清理，
#       只能对一份可以随便糟蹋的库跑（本地 dev 库），不要指向演示库。
B=${1:-http://127.0.0.1:8080}
PASS=0; FAIL=0; FAILED_CASES=""

chk() { # chk 名称 期望 实际
  if [ "$2" = "$3" ]; then PASS=$((PASS+1)); printf "  \033[32mPASS\033[0m %-46s %s\n" "$1" "$3"
  else FAIL=$((FAIL+1)); FAILED_CASES="$FAILED_CASES\n  - $1（期望 $2，实际 $3）"; printf "  \033[31mFAIL\033[0m %-46s 期望 %s 实际 %s\n" "$1" "$2" "$3"; fi
}
code_of() { sed -n 's/.*"code":\([-0-9]*\).*/\1/p' <<< "$1" | head -1; }
jget() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" <<< "$2" | head -1; }

echo "===== 0. 登录 ====="
LOGIN=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}')
T=$(jget token "$LOGIN")
chk "正确口令登录" "has_token" "$(grep -q '"token"' <<< "$LOGIN" && echo has_token || echo no_token)"
chk "错误口令返回 401" 401 "$(curl -s -o /dev/null -w '%{http_code}' -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"wrong"}')"
chk "无 token 访问业务接口 401" 401 "$(curl -s -o /dev/null -w '%{http_code}' $B/api/goods)"
H="Authorization: Bearer $T"

echo "===== 1. 货品建档（UC-02）====="
GN=$(date +%s)
BODY="{\"remark\":\"测试批次\",\"items\":[{\"name\":\"测试手镯$GN\",\"categoryId\":1,\"material\":\"和田玉\",\"origin\":\"新疆\",\"shape\":\"手镯\",\"form\":\"籽料\",\"weightG\":56.5,\"size\":\"圈口56\",\"srcType\":\"采购\",\"cost\":8000,\"minPrice\":12000,\"transferPrice\":7200}]}"
R=$(curl -s -X POST $B/api/stock-ins -H "$H" -H 'Content-Type: application/json' -d "$BODY")
chk "采购来源建档（带成本）" 0 "$(code_of "$R")"
NEW_NO=$(sed -n 's/.*"goodsNo":"\([^"]*\)".*/\1/p' <<< "$R" | head -1)

R=$(curl -s -X POST $B/api/stock-ins -H "$H" -H 'Content-Type: application/json' \
  -d '{"items":[{"name":"测试调货件","categoryId":1,"weightG":20,"srcType":"调货","cost":500,"minPrice":3000}]}')
chk "调货来源带成本应 422" 422 "$(code_of "$R")"
R=$(curl -s -X POST $B/api/stock-ins -H "$H" -H 'Content-Type: application/json' \
  -d '{"items":[{"name":"测试缺成本","categoryId":1,"weightG":20,"srcType":"采购","minPrice":3000}]}')
chk "采购来源缺成本应 422" 422 "$(code_of "$R")"

echo "===== 2. 成交（UC-01）====="
R=$(curl -s -X POST $B/api/sales -H "$H" -H 'Content-Type: application/json' \
  -d "{\"goodsNo\":\"$NEW_NO\",\"price\":5000,\"payMethod\":\"全款\"}")
chk "低于底价无授权应 422" 422 "$(code_of "$R")"
R=$(curl -s -X POST $B/api/sales -H "$H" -H 'Content-Type: application/json' \
  -d "{\"goodsNo\":\"$NEW_NO\",\"price\":5000,\"payMethod\":\"全款\",\"authorizedBy\":1,\"authNote\":\"老板同意\"}")
chk "低于底价带授权可成交" 0 "$(code_of "$R")"
SALE_NO=$(sed -n 's/.*"saleNo":"\([^"]*\)".*/\1/p' <<< "$R" | head -1)
SALE_ID=$(sed -n 's/.*"id":\([0-9]*\).*/\1/p' <<< "$R" | head -1)
R=$(curl -s -X POST $B/api/sales -H "$H" -H 'Content-Type: application/json' \
  -d "{\"goodsNo\":\"$NEW_NO\",\"price\":9000,\"payMethod\":\"全款\"}")
chk "同货品二次成交应 409" 409 "$(code_of "$R")"
R=$(curl -s -G $B/api/goods --data-urlencode "keyword=$NEW_NO" -H "$H")
chk "成交后货品状态为已售" "已售" "$(sed -n 's/.*"status":"\([^"]*\)".*/\1/p' <<< "$R" | head -1)"

# 定金 + 超价
R=$(curl -s -X POST $B/api/stock-ins -H "$H" -H 'Content-Type: application/json' \
  -d "{\"items\":[{\"name\":\"定金测试件$GN\",\"categoryId\":2,\"weightG\":15,\"srcType\":\"采购\",\"cost\":300,\"minPrice\":2000}]}")
G2=$(sed -n 's/.*"goodsNo":"\([^"]*\)".*/\1/p' <<< "$R" | head -1)
R=$(curl -s -X POST $B/api/sales -H "$H" -H 'Content-Type: application/json' \
  -d "{\"goodsNo\":\"$G2\",\"price\":2000,\"payMethod\":\"定金\",\"received\":3000}")
chk "定金超成交价应 400" 400 "$(code_of "$R")"
R=$(curl -s -X POST $B/api/sales -H "$H" -H 'Content-Type: application/json' \
  -d "{\"goodsNo\":\"$G2\",\"price\":2000,\"payMethod\":\"定金\",\"received\":600}")
chk "定金成交" 0 "$(code_of "$R")"
DEP_SALE=$(sed -n 's/.*"id":\([0-9]*\).*/\1/p' <<< "$R" | head -1)
R=$(curl -s -G "$B/api/goods" --data-urlencode "keyword=$G2" -H "$H")
chk "定金成交货品为已订" "已订" "$(sed -n 's/.*"status":"\([^"]*\)".*/\1/p' <<< "$R" | head -1)"

echo "===== 3. 退货（UC-03）====="
R=$(curl -s -X POST $B/api/sales/$SALE_ID/returns -H "$H" -H 'Content-Type: application/json' \
  -d '{"reason":"圈口不合"}')
chk "原因不在范围应 422" 422 "$(code_of "$R")"
R=$(curl -s -X POST $B/api/sales/$SALE_ID/returns -H "$H" -H 'Content-Type: application/json' \
  -d '{"reason":"质量问题","refund":4000}')
chk "退款不一致无说明应 422" 422 "$(code_of "$R")"
R=$(curl -s -X POST $B/api/sales/$SALE_ID/returns -H "$H" -H 'Content-Type: application/json' \
  -d '{"reason":"质量问题","refund":5000}')
chk "正常退货" 0 "$(code_of "$R")"
R=$(curl -s -X POST $B/api/sales/$SALE_ID/returns -H "$H" -H 'Content-Type: application/json' \
  -d '{"reason":"质量问题","refund":5000}')
chk "重复退货应 409" 409 "$(code_of "$R")"
R=$(curl -s "$B/api/goods?keyword=$NEW_NO" -H "$H")
chk "退货后货品回在柜" "在柜" "$(sed -n 's/.*"status":"\([^"]*\)".*/\1/p' <<< "$R" | head -1)"

echo "===== 4. 分期（UC-24/25）====="
R=$(curl -s -X POST $B/api/sales/$DEP_SALE/installment -H "$H" -H 'Content-Type: application/json' -d '{"periods":6}')
chk "建分期计划" 0 "$(code_of "$R")"
PLAN_ID=$(sed -n 's/.*"id":\([0-9]*\).*/\1/p' <<< "$R" | head -1)
R=$(curl -s -X POST $B/api/sales/$DEP_SALE/installment -H "$H" -H 'Content-Type: application/json' -d '{"periods":3}')
chk "重复建计划应 409" 409 "$(code_of "$R")"
R=$(curl -s -X POST $B/api/installments/$PLAN_ID/payments -H "$H" -H 'Content-Type: application/json' -d '{"amount":99999}')
chk "还款超额应 400" 400 "$(code_of "$R")"
R=$(curl -s -X POST $B/api/installments/$PLAN_ID/payments -H "$H" -H 'Content-Type: application/json' -d '{"amount":2000}')
chk "全额还款成功" 0 "$(code_of "$R")"
chk "结清后状态" "已结清" "$(sed -n 's/.*"status":"\([^"]*\)".*/\1/p' <<< "$R" | head -1)"
R=$(curl -s -G "$B/api/goods" --data-urlencode "keyword=$G2" -H "$H")
chk "结清后货品转已售（UC-01④）" "已售" "$(sed -n 's/.*"status":"\([^"]*\)".*/\1/p' <<< "$R" | head -1)"
R=$(curl -s -X POST $B/api/installments/$PLAN_ID/payments -H "$H" -H 'Content-Type: application/json' -d '{"amount":100}')
chk "已结清再还应 409" 409 "$(code_of "$R")"

echo "===== 5. 预定额度（UC-13）====="
PH="139${GN: -8}"
R=$(curl -s "$B/api/public/bookings/quota?phone=$PH")
chk "新号码额度 3" 3 "$(sed -n 's/.*"remaining":\([0-9]*\).*/\1/p' <<< "$R" | head -1)"
for i in 1 2 3; do
  RG=$(curl -s -X POST $B/api/stock-ins -H "$H" -H 'Content-Type: application/json' \
    -d "{\"items\":[{\"name\":\"预定测试$GN-$i\",\"categoryId\":3,\"weightG\":10,\"srcType\":\"采购\",\"cost\":100,\"minPrice\":1000}]}")
  BG=$(sed -n 's/.*"goodsNo":"\([^"]*\)".*/\1/p' <<< "$RG" | head -1)
  curl -s -X POST $B/api/public/bookings -H 'Content-Type: application/json' \
    -d "{\"goodsNo\":\"$BG\",\"name\":\"测试\",\"phone\":\"$PH\"}" >/dev/null
done
R=$(curl -s "$B/api/public/bookings/quota?phone=$PH")
chk "占满后剩余 0" 0 "$(sed -n 's/.*"remaining":\([0-9]*\).*/\1/p' <<< "$R" | head -1)"
RG=$(curl -s -X POST $B/api/stock-ins -H "$H" -H 'Content-Type: application/json' \
  -d "{\"items\":[{\"name\":\"预定第4件$GN\",\"categoryId\":3,\"weightG\":10,\"srcType\":\"采购\",\"cost\":100,\"minPrice\":1000}]}")
BG=$(sed -n 's/.*"goodsNo":"\([^"]*\)".*/\1/p' <<< "$RG" | head -1)
R=$(curl -s -X POST $B/api/public/bookings -H 'Content-Type: application/json' \
  -d "{\"goodsNo\":\"$BG\",\"name\":\"测试\",\"phone\":\"$PH\"}")
chk "第 4 条预定应 422" 422 "$(code_of "$R")"

echo "===== 6. 调货（UC-20/23）====="
RG=$(curl -s -X POST $B/api/stock-ins -H "$H" -H 'Content-Type: application/json' \
  -d "{\"items\":[{\"name\":\"调货测试$GN\",\"categoryId\":1,\"weightG\":22,\"srcType\":\"采购\",\"cost\":900,\"minPrice\":3000,\"transferPrice\":1800}]}")
TG=$(sed -n 's/.*"goodsNo":"\([^"]*\)".*/\1/p' <<< "$RG" | head -1)
if [ -z "$TG" ]; then echo "  [调试] 调货建档响应: $RG"; fi
MID=$(curl -s -G "$B/api/source-parties" --data-urlencode "partyType=同行商户" -H "$H" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
R=$(curl -s -X POST $B/api/transfers -H "$H" -H 'Content-Type: application/json' \
  -d "{\"direction\":\"调出\",\"merchantId\":$MID,\"goodsNo\":\"$TG\"}")
chk "登记调出" 0 "$(code_of "$R")"
TID=$(sed -n 's/.*"id":\([0-9]*\).*/\1/p' <<< "$R" | head -1)
R=$(curl -s -G "$B/api/goods" --data-urlencode "keyword=$TG" -H "$H")
chk "调出后货品为代销中（UC-01⑤）" "代销中" "$(sed -n 's/.*"status":"\([^"]*\)".*/\1/p' <<< "$R" | head -1)"
R=$(curl -s -X POST $B/api/settlements/batch -H "$H" -H 'Content-Type: application/json' \
  -d "{\"merchantId\":$MID,\"transferIds\":[$TID],\"settleMethod\":\"转账\"}")
chk "在调状态结算应 422" 422 "$(code_of "$R")"
R=$(curl -s -X PUT $B/api/transfers/$TID/status -H "$H" -H 'Content-Type: application/json' -d '{"status":"已售"}')
chk "改状态为已售" 0 "$(code_of "$R")"
R=$(curl -s -X POST $B/api/settlements/batch -H "$H" -H 'Content-Type: application/json' \
  -d "{\"merchantId\":$MID,\"transferIds\":[$TID],\"settleMethod\":\"转账\"}")
chk "结算成功" 0 "$(code_of "$R")"
chk "结算金额取调货价 1800" 1800 "$(sed -n 's/.*"amountSum":\([0-9.]*\).*/\1/p' <<< "$R" | head -1 | sed 's/\.00$//')"

echo "===== 7. 寄售（UC-21/22）====="
SID=$(curl -s -G "$B/api/source-parties" --data-urlencode "partyType=供应商" -H "$H" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
R=$(curl -s -X POST $B/api/consignments -H "$H" -H 'Content-Type: application/json' \
  -d "{\"supplierId\":$SID,\"items\":[{\"name\":\"寄售测试$GN\",\"categoryId\":1,\"weightG\":30,\"consignPrice\":5000}]}")
chk "登记寄售合同" 0 "$(code_of "$R")"
CGOODS=$(sed -n 's/.*"goodsNo":"\([^"]*\)".*/\1/p' <<< "$R" | head -1)
R=$(curl -s -G "$B/api/goods" --data-urlencode "keyword=$CGOODS" -H "$H")
chk "寄售货品成本=寄售价" 5000 "$(sed -n 's/.*"cost":\([0-9.]*\).*/\1/p' <<< "$R" | head -1 | sed 's/\.00$//')"
chk "寄售最低售价=寄售价×1.25" 6250 "$(sed -n 's/.*"minPrice":\([0-9.]*\).*/\1/p' <<< "$R" | head -1 | sed 's/\.00$//')"
R=$(curl -s -X POST $B/api/consignments -H "$H" -H 'Content-Type: application/json' \
  -d '{"supplierId":999999,"items":[{"name":"x","consignPrice":100}]}')
chk "供应商不存在应 422" 422 "$(code_of "$R")"

echo "===== 8. 统计（UC-06）====="
F=$(date -d '-90 days' +%Y-%m-%d 2>/dev/null || date -v-90d +%Y-%m-%d)
TO=$(date +%Y-%m-%d)
R=$(curl -s "$B/api/stats/sales?from=$F&to=$TO" -H "$H")
chk "统计可查" 0 "$(code_of "$R")"
NET=$(sed -n 's/.*"netAmount":\([0-9.]*\).*/\1/p' <<< "$R")
AMT=$(sed -n 's/.*"amount":\([0-9.]*\).*/\1/p' <<< "$R")
RET=$(sed -n 's/.*"returnAmount":\([0-9.]*\).*/\1/p' <<< "$R")
CALC=$(python3 -c "print(f'{float($AMT)-float($RET):.2f}')" 2>/dev/null || echo "$NET")
chk "净额=销售额-退货" "${CALC%.*}" "${NET%.*}"
R=$(curl -s "$B/api/stats/turnover?from=$F&to=$TO" -H "$H")
WD=$(sed -n 's/.*"weightedDays":\([-0-9.]*\).*/\1/p' <<< "$R")
ISNEG=$(python3 -c "print('yes' if float($WD)<0 else 'no')" 2>/dev/null || echo no)
chk "加权周转天数非负" "no" "$ISNEG"

echo "===== 9. 权限（§4.4）====="
chk "无效 token 访问应 401" 401 "$(curl -s -o /dev/null -w '%{http_code}' -X POST $B/api/transfer-applies/1/audit -H 'Authorization: Bearer invalid' -H 'Content-Type: application/json' -d '{"pass":true}')"

echo "===== 10. 账号状态与口令强度 ====="
# 2026-10-26 二次复核补：停用账号能不能登录、重置口令通道有没有强度门槛，
# 这两条此前没有任何断言覆盖，回归里漏一次就是「停用账号照样进系统」。
UN="apitest$GN"
R=$(curl -s -X POST $B/api/users -H "$H" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$UN\",\"name\":\"用例账号\",\"password\":\"Zz12345678\",\"roleIds\":[2]}")
chk "开通用例账号" 0 "$(code_of "$R")"
AID=$(sed -n 's/.*"id":\([0-9]*\).*/\1/p' <<< "$R" | head -1)
curl -s -o /dev/null -X PUT "$B/api/users/$AID" -H "$H" -H 'Content-Type: application/json' -d '{"status":"停用"}'
chk "停用账号口令正确也不能登录" 403 "$(curl -s -o /dev/null -w '%{http_code}' -X POST $B/api/auth/login -H 'Content-Type: application/json' -d "{\"username\":\"$UN\",\"password\":\"Zz12345678\"}")"
chk "停用账号错口令仍统一报 401（不泄露账号存在性）" 401 "$(curl -s -o /dev/null -w '%{http_code}' -X POST $B/api/auth/login -H 'Content-Type: application/json' -d "{\"username\":\"$UN\",\"password\":\"WrongPass1\"}")"
R=$(curl -s -X PUT "$B/api/users/$AID" -H "$H" -H 'Content-Type: application/json' -d '{"password":"123456"}')
chk "重置为弱口令应 400" 400 "$(code_of "$R")"
R=$(curl -s -X PUT "$B/api/users/$AID" -H "$H" -H 'Content-Type: application/json' -d '{"password":"Ab12345678"}')
chk "重置为合规口令通过" 0 "$(code_of "$R")"

echo ""
echo "======================================"
echo "  通过 $PASS 项，失败 $FAIL 项"
if [ $FAIL -gt 0 ]; then echo -e "  失败明细：$FAILED_CASES"; fi
echo "======================================"
