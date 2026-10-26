#!/bin/bash
# 从备份包恢复数据库。默认恢复到一个新库做演练，绝不碰线上库。
#
#   bash /opt/jade/restore-db.sh /opt/jade/backup/db_jade-20261026-1215.sql.gz
#       → 恢复到 db_jade_restore，用来验包能不能用、看里面的数据
#   bash /opt/jade/restore-db.sh <包> --into-live --yes
#       → 真要回滚线上：先把当前 db_jade 存成 db_jade_before_restore_<时间>，再用包覆盖 db_jade
#
# 恢复完记得把容器重启一次（bash /opt/jade/run-app.sh），应用连的是库，不用重建。
set -uo pipefail

F=${1:-}
MODE=${2:-}
CONF=/opt/jade/backup.conf
[ -f "$CONF" ] && . "$CONF"
DB_NAME=${DB_NAME:-db_jade}
DB_CONTAINER=${DB_CONTAINER:-jade-db}
DB_ROOT_PWD=${DB_ROOT_PWD:-$(docker exec "$DB_CONTAINER" printenv MYSQL_ROOT_PASSWORD 2>/dev/null || true)}
DB_ROOT_PWD=${DB_ROOT_PWD:-<DB_PASSWORD>}
[ -n "$F" ] || { echo '用法：restore-db.sh <备份包> [--into-live --yes]'; exit 2; }
[ -f "$F" ] || { echo "找不到文件 $F"; exit 2; }
gzip -t "$F" || { echo '包体已损坏，换一个'; exit 1; }

Q() { docker exec "$DB_CONTAINER" mysql -uroot -p"$DB_ROOT_PWD" -N -e "$1" 2>/dev/null; }

if [ "$MODE" = "--into-live" ]; then
  [ "${3:-}" = "--yes" ] || { echo '覆盖线上库必须再加 --yes，确认你确实要回滚'; exit 2; }
  SNAP="${DB_NAME}_before_$(date +%Y%m%d-%H%M)"
  echo "1/3 先把当前线上库另存为 $SNAP（万一要回退）"
  Q "DROP DATABASE IF EXISTS $SNAP; CREATE DATABASE $SNAP CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
  docker exec "$DB_CONTAINER" mysqldump -uroot -p"$DB_ROOT_PWD" --single-transaction "$DB_NAME" 2>/dev/null \
    | docker exec -i "$DB_CONTAINER" mysql -uroot -p"$DB_ROOT_PWD" "$SNAP"
  echo "2/3 清空 $DB_NAME 并灌入 $F"
  Q "DROP DATABASE IF EXISTS $DB_NAME; CREATE DATABASE $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
  gunzip -c "$F" | docker exec -i "$DB_CONTAINER" mysql -uroot -p"$DB_ROOT_PWD" "$DB_NAME" || { echo '灌包失败，用快照恢复：库名 '$SNAP; exit 1; }
  CHECK_DB=$DB_NAME
else
  TGT="${DB_NAME}_restore"
  CHECK_DB=$TGT
  echo "1/2 恢复到演练库 $TGT（线上 $DB_NAME 不动）"
  Q "DROP DATABASE IF EXISTS $TGT; CREATE DATABASE $TGT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
  gunzip -c "$F" | docker exec -i "$DB_CONTAINER" mysql -uroot -p"$DB_ROOT_PWD" "$TGT" || { echo '灌包失败'; exit 1; }
fi

echo "2/2 结果核对"
Q "SELECT CONCAT('目标库 ${CHECK_DB} 表数=',COUNT(*)) FROM information_schema.tables WHERE table_schema='${CHECK_DB}'" 2>/dev/null
Q "SELECT CONCAT('货品=',COUNT(*),'  成交=',(SELECT COUNT(*) FROM \`${CHECK_DB}\`.t_sale),'  客户=',(SELECT COUNT(*) FROM \`${CHECK_DB}\`.t_customer)) FROM \`${CHECK_DB}\`.t_goods"
