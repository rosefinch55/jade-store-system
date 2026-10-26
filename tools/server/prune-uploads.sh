#!/bin/bash
# 隆圭琢玉 · 上传目录清理
#
# 用途：找出 /opt/jade/uploads 里没有任何数据库记录引用的文件。
# 默认只看不删，加 --delete 才真删；加 --days N 只处理 N 天前的文件（默认 7）。
#
# 引用只可能来自三处：t_goods.image、t_apply_image.image_path、t_delivery_image.image_path。
# 反查方式是把库里出现过的文件名字段拉出来比对——不猜路径前缀，避免换了存储就全判成孤儿。
#
# 建议节奏：每月 1 号手跑一次 dry-run，看一眼清单再决定删。不挂定时任务：
# 上传量本来就小，误删一张完工照片的代价远高于省下的几十 MB。
set -uo pipefail

DB_NAME=${DB_NAME:-db_jade}
DB_USER=${DB_USER:-root}
DB_PWD=${DB_PWD:-}
ROOT=${UPLOAD_ROOT:-/opt/jade/uploads}
DAYS=7
DO_DELETE=0

while [ $# -gt 0 ]; do
  case "$1" in
    --delete) DO_DELETE=1 ;;
    --days) shift; DAYS=${1:-7} ;;
    --root) shift; ROOT=${1:-$ROOT} ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "未知参数：$1" >&2; exit 2 ;;
  esac
  shift
done

[ -d "$ROOT" ] || { echo "目录不存在：$ROOT" >&2; exit 1; }

mysql_q() {
  if [ -n "$DB_PWD" ]; then
    docker exec -i jade-db mysql -u"$DB_USER" -p"$DB_PWD" -N -B --default-character-set=utf8mb4 "$DB_NAME" 2>/dev/null
  else
    docker exec -i jade-db mysql -u"$DB_USER" -N -B --default-character-set=utf8mb4 "$DB_NAME" 2>/dev/null
  fi
}

# 库里出现过的文件名（只取 basename，路径格式变了也不影响判断）
REFS=$(printf "%s\n" \
  "select coalesce(image,'') from t_goods;" \
  "select coalesce(image_path,'') from t_apply_image;" \
  "select coalesce(image_path,'') from t_delivery_image;" \
  | mysql_q | tr '\\' '/' | sed 's#.*/##' | grep -v '^$' | sort -u)

[ -z "$REFS" ] && { echo "没读到任何引用记录，先检查数据库连接，别当成“全是垃圾”。" >&2; exit 1; }

TOTAL=0; ORPHAN=0; ORPHAN_BYTES=0
LIST=$(mktemp)
while IFS= read -r f; do
  TOTAL=$((TOTAL+1))
  base=$(basename "$f")
  if printf "%s\n" "$REFS" | grep -qxF "$base"; then
    continue
  fi
  if [ "$DAYS" -gt 0 ] && [ -n "$(find "$f" -mtime -"$DAYS" 2>/dev/null)" ]; then
    continue   # 太新，可能是刚传完还没落库
  fi
  ORPHAN=$((ORPHAN+1))
  ORPHAN_BYTES=$((ORPHAN_BYTES + $(stat -c%s "$f" 2>/dev/null || echo 0)))
  echo "$f" >> "$LIST"
done < <(find "$ROOT" -type f)

echo "扫描 $ROOT：共 $TOTAL 个文件，无引用且超过 ${DAYS} 天的 $ORPHAN 个，合计 $ORPHAN_BYTES 字节"
if [ "$ORPHAN" -gt 0 ]; then
  sed 's/^/  /' "$LIST"
fi

if [ "$DO_DELETE" -eq 1 ] && [ "$ORPHAN" -gt 0 ]; then
  while IFS= read -r f; do rm -f -- "$f"; done < "$LIST"
  echo "已删除 $ORPHAN 个文件"
elif [ "$ORPHAN" -gt 0 ]; then
  echo "以上仅为清单，加 --delete 才真删"
fi
rm -f "$LIST"
