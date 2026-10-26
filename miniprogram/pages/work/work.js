const api = require('../../utils/api.js');

const CARDS = {
  SUPPLIER: [
    { t: '提交出让申请', d: '玉料、成品、回流三类，等门店审核', url: '/pages/supplier/supplier' },
    { t: '预约上门', d: '申请通过后约上门时间', url: '/pages/visit/visit' }
  ],
  CRAFTSMAN: [
    { t: '我的加工任务', d: '在加工与待交付，可提交完工', url: '/pages/craftsman/craftsman' },
    { t: '预约上门', d: '交付确认后约上门时间', url: '/pages/visit/visit' }
  ],
  MERCHANT: [
    { t: '调货价目', d: '在柜可调货品与调货价', url: '/pages/merchant/merchant' }
  ],
  BOSS: [
    { t: '经营数据在电脑端', d: '老板账号请用柜台电脑端查看与操作', url: '' }
  ],
  SALE: [
    { t: '销售操作在电脑端', d: '销售账号请用柜台电脑端开单', url: '' }
  ]
};

const ROLE_NAME = {
  SUPPLIER: '供应商',
  CRAFTSMAN: '加工师傅',
  MERCHANT: '合作商户',
  BOSS: '老板',
  SALE: '销售'
};

Page({
  data: {
    user: null,
    role: '',
    roleName: '',
    cards: []
  },

  onShow() {
    const u = api.user();
    if (!u || !api.token()) {
      wx.redirectTo({ url: '/pages/login/login' });
      return;
    }
    const role = api.roleCode();
    this.setData({
      user: u,
      role: role,
      roleName: ROLE_NAME[role] || '外部账号',
      cards: CARDS[role] || []
    });
  },

  open(e) {
    const url = e.currentTarget.dataset.url;
    if (!url) {
      wx.showModal({ title: '请用电脑端', content: '这个账号的日常操作在柜台电脑端完成。', showCancel: false });
      return;
    }
    wx.navigateTo({ url: url });
  },

  browse() {
    wx.reLaunch({ url: '/pages/index/index' });
  },

  logout() {
    api.clearSession();
    wx.reLaunch({ url: '/pages/index/index' });
  }
});
