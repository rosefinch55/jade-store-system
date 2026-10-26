const api = require('../../utils/api.js');

Page({
  data: {
    tail: '',
    results: [],
    queried: false,
    loading: false,
    err: ''
  },

  onTail(e) {
    this.setData({ tail: e.detail.value });
  },

  query() {
    const that = this;
    if (!/^\d{4}$/.test(that.data.tail)) {
      that.setData({ err: '请输入手机号后四位', queried: false });
      return;
    }
    that.setData({ loading: true, err: '' });
    api.req('/api/public/reservations' + api.qs({ phoneTail: that.data.tail })).then(function (r) {
      that.setData({
        loading: false,
        queried: true,
        results: r.code === 0 ? (r.data || []) : [],
        err: r.code === 0 ? '' : (r.message || '查询失败')
      });
    });
  },

  goHome() {
    wx.reLaunch({ url: '/pages/index/index' });
  },

  goAll() {
    wx.navigateTo({ url: '/pages/goods/goods' });
  }
});
