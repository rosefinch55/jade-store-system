const api = require('../../utils/api.js');

Page({
  data: {
    goodsNo: '',
    g: null,
    loading: true,
    err: ''
  },

  onLoad(options) {
    const no = options.goodsNo || '';
    this.setData({ goodsNo: no });
    this.load(no);
  },

  load(no) {
    const that = this;
    if (!no) {
      that.setData({ loading: false, err: '缺少货品编号' });
      return;
    }
    api.req('/api/public/goods/' + encodeURIComponent(no)).then(function (r) {
      if (r.code !== 0) {
        that.setData({ loading: false, err: r.message || '货品不存在或已下架' });
        return;
      }
      const g = r.data;
      g.imgUrl = g.image ? api.BASE + g.image : '';
      g.priceText = api.money(g.priceUpper);
      that.setData({ g: g, loading: false });
    });
  },

  noop() {},

  onImgErr() {
    this.setData({ 'g.imgUrl': '' });
  },

  goReserve() {
    wx.navigateTo({ url: '/pages/reserve/reserve?goodsNo=' + this.data.goodsNo });
  },
});
