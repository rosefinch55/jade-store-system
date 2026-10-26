const api = require('../../utils/api.js');

const TYPES = ['玉料', '成品', '回流'];

Page({
  data: {
    tab: 'form',
    types: TYPES,
    f: { outType: '玉料', name: '', quantity: '', expectPrice: '', description: '' },
    list: [],
    loading: false,
    busy: false,
    err: '',
    okMsg: ''
  },

  onLoad() {
    this.setData({ tab: 'form' });
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({ tab: tab, err: '', okMsg: '' });
    if (tab === 'list') this.loadList();
  },

  pickType(e) {
    this.setData({ 'f.outType': TYPES[Number(e.detail.value)] });
  },

  onInput(e) {
    const k = e.currentTarget.dataset.k;
    this.setData({ ['f.' + k]: e.detail.value, err: '' });
  },

  submit() {
    const f = this.data.f;
    if (!f.name.trim()) return this.setData({ err: '请填品名' });
    const q = Number(f.quantity);
    if (!q || q <= 0) return this.setData({ err: '数量必须是大于 0 的整数' });
    const p = Number(f.expectPrice);
    if (!(p >= 0)) return this.setData({ err: '请填期望出让价' });
    if (this.data.busy) return;

    this.setData({ busy: true, err: '', okMsg: '' });
    api.req('/api/supplier/applies', {
      method: 'POST',
      data: {
        outType: f.outType,
        name: f.name.trim(),
        quantity: q,
        expectPrice: p,
        description: f.description.trim()
      }
    }).then((r) => {
      this.setData({ busy: false });
      if (r.code !== 0) return this.setData({ err: r.message || '提交失败' });
      this.setData({
        okMsg: '已提交，申请号 ' + r.data.applyNo + '，等门店审核',
        f: { outType: f.outType, name: '', quantity: '', expectPrice: '', description: '' }
      });
    });
  },

  loadList() {
    this.setData({ loading: true });
    api.req('/api/supplier/applies').then((r) => {
      this.setData({
        loading: false,
        list: r.code === 0 ? (r.data || []) : [],
        err: r.code === 0 ? '' : (r.message || '加载失败')
      });
    });
  }
});
