const api = require('../../utils/api.js');

Page({
  data: {
    cats: [],
    catId: 0,
    items: [],
    total: 0,
    page: 1,
    size: 12,
    loading: false,
    hasMore: true,
    err: ''
  },

  onLoad() {
    this.loadCats();
    this.loadList(1);
  },

  loadCats() {
    const that = this;
    api.req('/api/public/categories').then(function (r) {
      if (r.code === 0) that.setData({ cats: r.data || [] });
    });
  },

  loadList(pageNo) {
    const that = this;
    if (that.data.loading) return;
    that.setData({ loading: true, err: '' });
    const q = { page: pageNo, size: that.data.size };
    if (that.data.catId) q.categoryId = that.data.catId;
    api.req('/api/public/goods' + api.qs(q)).then(function (r) {
      if (r.code !== 0) {
        that.setData({ loading: false, err: r.message || '加载失败' });
        return;
      }
      const decorated = that.decorate(r.data.items);
      const list = pageNo === 1 ? decorated : that.data.items.concat(decorated);
      that.setData({
        items: list,
        total: r.data.total,
        page: pageNo,
        loading: false,
        hasMore: list.length < r.data.total
      });
    });
  },

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

  pickCat(e) {
    const id = e.currentTarget.dataset.id;
    if (id === this.data.catId) return;
    this.setData({ catId: id, items: [], hasMore: true });
    this.loadList(1);
  },

  loadMore() {
    if (!this.data.hasMore || this.data.loading) return;
    this.loadList(this.data.page + 1);
  },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/detail/detail?goodsNo=' + e.currentTarget.dataset.no });
  },

  onImgErr(e) {
    const idx = e.currentTarget.dataset.idx;
    const patch = {};
    patch['items[' + idx + '].imgUrl'] = '';
    this.setData(patch);
  },

  goHome() {
    wx.navigateBack({ delta: 1 });
  },

  goMine() {
    wx.navigateTo({ url: '/pages/mine/mine' });
  }
});
