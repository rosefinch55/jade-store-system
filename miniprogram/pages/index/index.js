const api = require('../../utils/api.js');

Page({
  data: {
    shopName: '玉器门店',
    notice: '',
    featured: [],
    latest: [],
    total: 0,
    loading: true,
    err: ''
  },

  onLoad() {
    this.setData({
      shopName: getApp().globalData.shopName,
      notice: getApp().globalData.notice
    });
    this.loadAll();
  },

  onPullDownRefresh() {
    const that = this;
    this.loadAll(function () {
      wx.stopPullDownRefresh();
    });
  },

  loadAll(done) {
    const that = this;
    that.setData({ loading: true, err: '' });
    const p1 = api.req('/api/public/goods' + api.qs({ featured: 'true', page: 1, size: 10 }));
    const p2 = api.req('/api/public/goods' + api.qs({ page: 1, size: 8 }));
    Promise.all([p1, p2]).then(function (arr) {
      const f = arr[0];
      const a = arr[1];
      const featured = f.code === 0 ? that.decorate(f.data.items) : [];
      const latest = a.code === 0 ? that.decorate(a.data.items) : [];
      const total = a.code === 0 ? a.data.total : 0;
      that.setData({
        featured: featured,
        latest: latest,
        total: total,
        loading: false,
        err: a.code === 0 ? '' : (a.message || '加载失败')
      });
      if (done) done();
    });
  },

  /** 后端返回的是 /images/goods/xxx.jpg 相对路径，补全成可请求的绝对地址 */
  decorate(items) {
    const out = [];
    for (let i = 0; i < items.length; i++) {
      const g = items[i];
      out.push({
        goodsNo: g.goodsNo,
        name: g.name,
        categoryName: g.categoryName,
        weightG: g.weightG,
        featured: g.featured,
        priceUpper: g.priceUpper,
        priceText: api.money(g.priceUpper),
        imgUrl: g.image ? api.BASE + g.image : ''
      });
    }
    return out;
  },

  goDetail(e) {
    const no = e.currentTarget.dataset.no;
    wx.navigateTo({ url: '/pages/detail/detail?goodsNo=' + no });
  },

  goAll() {
    wx.navigateTo({ url: '/pages/goods/goods' });
  },

  goMine() {
    wx.navigateTo({ url: '/pages/mine/mine' });
  },

  /** 我的：已登录进工作台，未登录先去登录 */
  goWork() {
    const api = require('../../utils/api.js');
    wx.navigateTo({ url: api.token() ? '/pages/work/work' : '/pages/login/login' });
  },

  onImgErr(e) {
    const idx = e.currentTarget.dataset.idx;
    const key = e.currentTarget.dataset.key;
    const patch = {};
    patch[key + '[' + idx + '].imgUrl'] = '';
    this.setData(patch);
  }
});
