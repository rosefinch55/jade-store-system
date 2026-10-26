#!/usr/bin/env bash
# 清空预约与预定两张表，让小程序回归用例从干净状态起跑。
#
# 为什么需要：整件货独占是业务规则，上一轮用例留下的预约会把下一轮的货占死，
# 表现为「该货品已被预约」，与代码是否正确无关。
set -eu
KEY="C:/Users/ASUS/.ssh/id_ed25519"
ssh -i "$KEY" -o BatchMode=yes -o StrictHostKeyChecking=no root@203.0.113.10 \
  "docker exec jade-db mysql -uroot -p<DB_PASSWORD> -N -e \"
     delete from db_jade.t_reservation;
     delete from db_jade.t_booking;
     select concat('reservation=', (select count(*) from db_jade.t_reservation),
                   ' booking=', (select count(*) from db_jade.t_booking));\"" 2>/dev/null
