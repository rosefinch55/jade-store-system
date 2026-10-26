const api = require('../../utils/api.js');

Page({
  data: {
    list: [],
    page: 1,
    size: 20,
    total: 0,
    loading: false,
    err: ''
  },

  onLoad() {
    if (!api.token()) {
      wx.redirectTo({ url: '/pages/login/login' });
      return;
    }
    this.load(1);
  },

  load(p) {
    this.setData({ loading: true, err: '' });
    api.req('/api/merchant/price-list?page=' + p + '&size=' + this.data.size).then((r) => {
      if (r.code !== 0) {
        this.setData({ loading: false, err: r.message || '加载失败' });
        return;
      }
      const items = (r.data && r.data.items) || [];
      this.setData({
        loading: false,
        page: p,
        total: (r.data && r.data.total) || 0,
        list: p === 1 ? items : this.data.list.concat(items)
      });
    });
  },

  more() {
    if (this.data.list.length >= this.data.total) return;
    this.load(this.data.page + 1);
  }
});
