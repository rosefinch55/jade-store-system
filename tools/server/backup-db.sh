#!/bin/bash
# 隆圭琢玉 · 数据库备份
#
# 节奏：每 7 天一次（/etc/cron.d/jade-backup 里写的每周日 04:30），也可随时手跑。
# 落点：本地留 RETAIN_LOCAL 份（默认 4 份 = 4 周），外置硬盘留 RETAIN_EXT 份（默认 12 份 ≈ 3 个月）。
# 外置硬盘：把盘挂到 EXT_DIRS 里的任一路径就自动生效；没插只写一行日志，本次不算失败，
#           本地那份照留。要换挂载点或加第二个落点，改 /opt/jade/backup.conf，别改本脚本。
# 自检：每次备份后把包还原进一个临时库，比表数与关键表行数，验完删掉——
#       没验证过的备份不算备份。
set -uo pipefail

CONF=/opt/jade/backup.conf
[ -f "$CONF" ] && . "$CONF"

DB_NAME=${DB_NAME:-db_jade}
LOCAL_DIR=${LOCAL_DIR:-/opt/jade/backup}
EXT_DIRS=${EXT_DIRS:-/mnt/usb-jade/jade-backup}      # 空格分隔，可写多个
RETAIN_LOCAL=${RETAIN_LOCAL:-4}
RETAIN_EXT=${RETAIN_EXT:-12}
VERIFY=${VERIFY:-1}                                   # 0 = 跳过还原自检
DB_CONTAINER=${DB_CONTAINER:-jade-db}
# 口令优先从容器环境变量取，取不到才用配置里的（配置里也不写就等于没配）
DB_ROOT_PWD=${DB_ROOT_PWD:-$(docker exec "$DB_CONTAINER" printenv MYSQL_ROOT_PASSWORD 2>/dev/null || true)}
DB_ROOT_PWD=${DB_ROOT_PWD:-<DB_PASSWORD>}
MIN_FREE_MB=${MIN_FREE_MB:-300}

LOG_DIR="$LOCAL_DIR"
mkdir -p "$LOG_DIR"
say() { printf '%s %s\n' "$(date '+%F %T')" "$*"; }

MYSQL_Q() { docker exec "$DB_CONTAINER" mysql -uroot -p"$DB_ROOT_PWD" -N -e "$1" 2>/dev/null; }

TS=$(date +%Y%m%d-%H%M)
F="$LOCAL_DIR/${DB_NAME}-${TS}.sql.gz"
PART="$F.part"

FREE_MB=$(df -Pm "$LOCAL_DIR" | awk 'NR==2{print $4}')
if [ "${FREE_MB:-0}" -lt "$MIN_FREE_MB" ]; then
  say "中止：$LOCAL_DIR 只剩 ${FREE_MB}MB（门槛 ${MIN_FREE_MB}MB），先清旧备份或扩盘"
  exit 1
fi

say "开始备份 $DB_NAME → $F"
if ! docker exec "$DB_CONTAINER" mysqldump -uroot -p"$DB_ROOT_PWD" \
       --single-transaction --routines --events --set-gtid-purged=OFF \
       --default-character-set=utf8mb4 "$DB_NAME" 2>"$LOCAL_DIR/dump.err" | gzip -1 > "$PART"; then
  say "中止：mysqldump 失败，详见 $LOCAL_DIR/dump.err"
  rm -f "$PART"; exit 1
fi
if ! gzip -t "$PART"; then say "中止：包体自检不过"; rm -f "$PART"; exit 1; fi
mv "$PART" "$F"
SHA=$(sha256sum "$F" | cut -c1-16)
SIZE=$(du -h "$F" | cut -f1)
echo "$(sha256sum "$F")" > "$F.sha"
say "备份完成 $SIZE sha256=$SHA"

# 还原自检：单独建一个临时库，把包灌进去，比表数与关键表行数
if [ "$VERIFY" = "1" ]; then
  VDB="${DB_NAME}_verify"
  MYSQL_Q "DROP DATABASE IF EXISTS $VDB; CREATE DATABASE $VDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
  if gunzip -c "$F" | docker exec -i "$DB_CONTAINER" mysql -uroot -p"$DB_ROOT_PWD" "$VDB" 2>"$LOCAL_DIR/verify.err"; then
    NT=$(MYSQL_Q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$VDB'")
    SRC=$(MYSQL_Q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME'")
    ROWS=$(MYSQL_Q "SELECT CONCAT('货品=',COUNT(*)) FROM \`$DB_NAME\`.t_goods" )
    VROWS=$(MYSQL_Q "SELECT CONCAT('货品=',COUNT(*)) FROM \`$VDB\`.t_goods")
    if [ "$NT" = "$SRC" ] && [ "$ROWS" = "$VROWS" ]; then
      say "还原自检通过：$NT 张表（源库 $SRC），$VROWS（源库 $ROWS）"
    else
      say "警告：还原后对不上（表 $NT/$SRC，$VROWS vs $ROWS），这个包别拿去恢复"
    fi
  else
    say "警告：还原自检失败，详见 $LOCAL_DIR/verify.err"
  fi
  MYSQL_Q "DROP DATABASE IF EXISTS $VDB;"
fi

# 外置硬盘
for D in $EXT_DIRS; do
  if [ -d "$D" ] && [ -w "$D" ]; then
    cp -p "$F" "$F.sha" "$D/" && say "已导出到外置盘 $D"
    old=$(ls -1t "$D"/${DB_NAME}-*.sql.gz 2>/dev/null | tail -n +$((RETAIN_EXT + 1)))
    [ -n "$old" ] && { echo "$old" | xargs -r rm -f; echo "$old" | sed 's/\.gz$/.sha/' | xargs -r rm -f; }
  else
    say "外置盘未挂载（$D 不存在或不可写），本次只留本地一份"
  fi
done

# 本地保留
old=$(ls -1t "$LOCAL_DIR"/${DB_NAME}-*.sql.gz 2>/dev/null | tail -n +$((RETAIN_LOCAL + 1)))
if [ -n "$old" ]; then
  echo "$old" | xargs -r rm -f; echo "$old" | sed 's/\.gz$/.sha/' | xargs -r rm -f
  say "本地清理旧备份 $(echo "$old" | wc -l) 份（保留最近 $RETAIN_LOCAL 份）"
fi
say "当前本地留存：$(ls -1 "$LOCAL_DIR"/${DB_NAME}-*.sql.gz 2>/dev/null | wc -l) 份"
