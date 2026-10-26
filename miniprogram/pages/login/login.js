const api = require('../../utils/api.js');

Page({
  data: {
    username: '',
    password: '',
    busy: false,
    err: ''
  },

  onUser(e) { this.setData({ username: e.detail.value, err: '' }); },
  onPwd(e) { this.setData({ password: e.detail.value, err: '' }); },

  submit() {
    const u = this.data.username.trim();
    const p = this.data.password;
    if (!u || !p) return this.setData({ err: '请输入账号与口令' });
    if (this.data.busy) return;
    this.setData({ busy: true, err: '' });

    api.login(u, p).then((r) => {
      this.setData({ busy: false });
      if (r.code !== 0) {
        this.setData({ err: r.message || '登录失败' });
        return;
      }
      wx.redirectTo({ url: '/pages/work/work' });
    });
  },

  back() {
    wx.reLaunch({ url: '/pages/index/index' });
  }
});
