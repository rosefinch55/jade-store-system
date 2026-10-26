const api = require('../../utils/api.js');

Page({
  data: {
    role: '',
    applies: [],      // 供选择的来源单据
    applyIdx: 0,
    date: '',
    time: '10:00',
    matter: '',
    err: '',
    okMsg: '',
    busy: false
  },

  onLoad() {
    if (!api.token()) {
      wx.redirectTo({ url: '/pages/login/login' });
      return;
    }
    const role = api.roleCode();
    const d = new Date(Date.now() + 24 * 3600 * 1000);
    this.setData({
      role: role,
      date: api.fmtDate(d)
    });
    if (role === 'SUPPLIER') this.loadApplies();
  },

  /** 供应商：只有「已通过」的出让申请才能约上门 */
  loadApplies() {
    api.req('/api/supplier/applies').then((r) => {
      if (r.code !== 0) return this.setData({ err: r.message || '加载申请失败' });
      const ok = (r.data || []).filter((a) => a.status === '已通过');
      this.setData({ applies: ok });
    });
  },

  pickApply(e) {
    this.setData({ applyIdx: Number(e.detail.value) });
  },

  onDate(e) {
    this.setData({ date: e.detail.value });
  },

  onTime(e) {
    this.setData({ time: e.detail.value });
  },

  onMatter(e) {
    this.setData({ matter: e.detail.value, err: '' });
  },

  submit() {
    const a = this.data.applies[this.data.applyIdx];
    if (this.data.role === 'SUPPLIER' && !a) {
      return this.setData({ err: '还没有「已通过」的出让申请，先提交申请等门店审核' });
    }
    if (!this.data.date || !this.data.time) return this.setData({ err: '请选择上门日期与时间' });
    // 上门时段结构化（2026-10-26）：按半小时选，一次占一小时，与 external.html 同规则
    if (!/(^|:)(00|30)$/.test(this.data.time) || this.data.time < '10:00' || this.data.time > '19:00') {
      return this.setData({ err: '时段按半小时选，最早 10:00、最晚 19:00' });
    }
    if (this.data.busy) return;

    this.setData({ busy: true, err: '', okMsg: '' });
    api.req('/api/visit-appts', {
      method: 'POST',
      data: {
        matter: this.data.matter.trim() || ('上门办理 ' + (a ? a.applyNo : '交付')),
        expectDate: this.data.date,
        startTime: this.data.time,
        transferApplyId: a ? a.id : null
      }
    }).then((r) => {
      this.setData({ busy: false });
      if (r.code !== 0) return this.setData({ err: r.message || '预约失败' });
      this.setData({ okMsg: '已预约 ' + this.data.date + ' ' + this.data.time + ' 起一小时，等门店确认' });
    });
  }
});
