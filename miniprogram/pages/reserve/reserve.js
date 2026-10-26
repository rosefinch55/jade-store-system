const api = require('../../utils/api.js');

/** 可约窗口：当前时间 +2 小时 起，+3 天 止（与后端 CustomerController 的常量一致） */
const LEAD_HOURS = 2;
const SPAN_DAYS = 3;
/** 到店时间按半小时对齐 */
const STEP_MINUTES = 30;
const DAY_START = '09:00';
const DAY_END = '20:00';

function pad(n) {
  return n < 10 ? '0' + n : '' + n;
}

/** 从 startHm 到 endHm，每 30 分钟一个时段 */
function buildSlots(startHm, endHm) {
  const out = [];
  if (!startHm || !endHm) return out;
  const s = startHm.split(':');
  const e = endHm.split(':');
  let cur = Number(s[0]) * 60 + Number(s[1]);
  const end = Number(e[0]) * 60 + Number(e[1]);
  for (; cur <= end; cur += STEP_MINUTES) {
    out.push(pad(Math.floor(cur / 60)) + ':' + pad(cur % 60));
  }
  return out;
}

Page({
  data: {
    goodsNo: '',
    name: '',
    phone: '',
    date: '',
    time: '',
    slots: [],
    slotIndex: 0,
    minDate: '',
    maxDate: '',
    minText: '',
    maxText: '',
    submitting: false,
    err: ''
  },

  onLoad(options) {
    this.setData({ goodsNo: options.goodsNo || '' });
    this.refreshWindow();
  },

  onShow() {
    // 停留过久再回来时窗口可能已过期，重算
    this.refreshWindow();
  },

  /** 窗口起点：当前时间 +2 小时，向上取整到半小时 */
  windowStart(now) {
    const min = new Date(now.getTime() + LEAD_HOURS * 3600 * 1000);
    const m = min.getMinutes();
    let add = 0;
    if (m !== 0 && m !== 30) add = m < 30 ? 30 - m : 60 - m;
    min.setMinutes(m + add);
    min.setSeconds(0, 0);
    return min;
  },

  refreshWindow() {
    const now = new Date();
    const start = this.windowStart(now);
    const max = new Date(now.getTime() + SPAN_DAYS * 24 * 3600 * 1000);
    const minDate = api.fmtDate(now);
    // 窗口上界若落在营业开始前（如凌晨 00:30），该日没有可约时段，上界回退一天
    let maxDate = api.fmtDate(max);
    if (api.fmtHm(max) <= DAY_START) {
      maxDate = api.fmtDate(new Date(max.getTime() - 24 * 3600 * 1000));
    }
    const startHm = api.fmtHm(start);

    // 今天剩余时间已过营业结束，就从明天开始
    let date = minDate;
    let slots = buildSlots(startHm > DAY_START ? startHm : DAY_START, DAY_END);
    if (!slots.length) {
      date = api.fmtDate(new Date(now.getTime() + 24 * 3600 * 1000));
      slots = buildSlots(DAY_START, DAY_END);
    }

    this.setData({
      minDate: minDate,
      maxDate: maxDate,
      minText: api.fmtDate(start) + ' ' + startHm,
      maxText: maxDate + ' ' + (api.fmtHm(max) < DAY_END ? api.fmtHm(max) : DAY_END),
      date: date,
      slots: slots,
      slotIndex: 0,
      time: slots.length ? slots[0] : '',
      err: ''
    });
  },

  onDate(e) {
    const date = e.detail.value;
    const now = new Date();
    const start = this.windowStart(now);
    const max = new Date(now.getTime() + SPAN_DAYS * 24 * 3600 * 1000);

    const maxDay = api.fmtHm(max) <= DAY_START
      ? api.fmtDate(new Date(max.getTime() - 24 * 3600 * 1000)) : api.fmtDate(max);
    const startHm = date === api.fmtDate(now) ? api.fmtHm(start) : DAY_START;
    // 末日按「窗口上界」与「营业结束」取早的那个：窗口只截到 21:50 也不该给出 21:30 的档
    const winEnd = api.fmtHm(max);
    const endHm = (date === maxDay && winEnd > DAY_START && winEnd < DAY_END) ? winEnd : DAY_END;
    const slots = buildSlots(startHm, endHm);

    this.setData({
      date: date,
      slots: slots,
      slotIndex: 0,
      time: slots.length ? slots[0] : '',
      err: slots.length ? '' : '该日期没有可约时段，请换一天'
    });
  },

  onSlot(e) {
    const i = Number(e.detail.value);
    this.setData({ slotIndex: i, time: this.data.slots[i] || '', err: '' });
  },

  onName(e) {
    this.setData({ name: e.detail.value });
  },

  onPhone(e) {
    this.setData({ phone: e.detail.value });
  },

  submit() {
    const that = this;
    const d = that.data;
    if (!d.name) {
      that.setData({ err: '请填写姓名' });
      return;
    }
    if (!/^\d{11}$/.test(d.phone)) {
      that.setData({ err: '手机号需 11 位' });
      return;
    }
    if (!d.date || !d.time) {
      that.setData({ err: '请选择到店日期与时间' });
      return;
    }

    // iOS 不认 yyyy-MM-dd HH:mm，换斜杠再解析
    const picked = new Date((d.date + ' ' + d.time).replace(/-/g, '/')).getTime();
    const now = Date.now();
    const minMs = now + LEAD_HOURS * 3600 * 1000;
    const maxMs = now + SPAN_DAYS * 24 * 3600 * 1000;
    if (isNaN(picked)) {
      that.setData({ err: '时间格式不正确' });
      return;
    }
    if (picked < minMs) {
      that.setData({ err: '最早可约 ' + d.minText });
      return;
    }
    if (picked > maxMs) {
      that.setData({ err: '最晚可约 ' + d.maxText });
      return;
    }

    that.setData({ submitting: true, err: '' });
    api.req('/api/public/reservations', {
      method: 'POST',
      data: {
        goodsNo: d.goodsNo,
        name: d.name,
        phone: d.phone,
        expectSlot: d.date + ' ' + d.time
      }
    }).then(function (r) {
      that.setData({ submitting: false });
      if (r.code !== 0) {
        that.setData({ err: r.message || '预约失败' });
        return;
      }
      const tip = r.data.tip || '请截图保存，到店报编号。';
      wx.showModal({
        title: '预约成功',
        content: '预约编号：' + r.data.resvNo + '\n到店时间：' + r.data.expectSlot + '\n\n' + tip,
        confirmText: '查看预约',
        cancelText: '知道了',
        success: function (res) {
          if (res.confirm) {
            wx.redirectTo({ url: '/pages/mine/mine' });
          } else {
            wx.navigateBack({ delta: 1 });
          }
        }
      });
    });
  }
});
