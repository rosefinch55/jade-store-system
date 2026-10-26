const api = require('../../utils/api.js');

const STAGES = ['加工前', '加工中', '完工'];

Page({
  data: {
    list: [],
    loading: false,
    err: '',
    showDialog: false,
    cur: null,
    shots: [],   // [{ stage, path }]，三个阶段各一张
    note: '',
    busy: false
  },

  onShow() {
    if (!api.token()) {
      wx.redirectTo({ url: '/pages/login/login' });
      return;
    }
    this.load();
  },

  load() {
    this.setData({ loading: true, err: '' });
    api.req('/api/craftsman/tasks?status=' + encodeURIComponent('在加工,待交付')).then((r) => {
      if (r.code !== 0) {
        this.setData({ loading: false, err: r.message || '加载失败', list: [] });
        return;
      }
      this.setData({ loading: false, list: (r.data && r.data.items) || [] });
    });
  },

  open(e) {
    const id = e.currentTarget.dataset.id;
    const cur = this.data.list.filter((t) => String(t.id) === String(id))[0];
    if (!cur) return;
    if (cur.status !== '在加工') {
      wx.showModal({
        title: '暂不能提交',
        content: '任务当前是「' + cur.status + '」，只有「在加工」才能提交完工。',
        showCancel: false
      });
      return;
    }
    this.setData({
      showDialog: true,
      cur: cur,
      note: '',
      err: '',
      shots: STAGES.map((s) => ({ stage: s, path: '' }))
    });
  },

  close() {
    this.setData({ showDialog: false, busy: false });
  },

  onNote(e) {
    this.setData({ note: e.detail.value });
  },

  /** 选图后立即读成 base64 上传，拿到服务端路径才算完成这一步 */
  pick(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    const that = this;
    wx.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['camera', 'album'],
      success(res) {
        const path = res.tempFilePaths[0];
        wx.getFileSystemManager().readFile({
          filePath: path,
          encoding: 'base64',
          success(f) {
            that.setData({ err: '' });
            api.req('/api/upload/base64', {
              method: 'POST',
              data: { name: path.split('/').pop(), data: f.data }
            }).then((r) => {
              if (r.code !== 0) return that.setData({ err: r.message || '图片上传失败' });
              const shots = that.data.shots.slice();
              shots[idx].path = r.data.path;
              that.setData({ shots: shots });
            });
          },
          fail() { that.setData({ err: '读取图片失败' }); }
        });
      }
    });
  },

  submit() {
    const missing = this.data.shots.filter((s) => !s.path).map((s) => s.stage);
    if (missing.length) return this.setData({ err: '还缺：' + missing.join('、') + ' 的照片' });
    if (this.data.busy) return;

    this.setData({ busy: true, err: '' });
    api.req('/api/craftsman/tasks/' + this.data.cur.id + '/delivery', {
      method: 'POST',
      data: {
        note: this.data.note,
        images: this.data.shots.map((s) => ({ stage: s.stage, path: s.path }))
      }
    }).then((r) => {
      this.setData({ busy: false });
      if (r.code !== 0) return this.setData({ err: r.message || '提交失败' });
      this.setData({ showDialog: false });
      wx.showModal({
        title: '已提交完工',
        content: '申请号 ' + r.data.applyNo + '，等门店确认后可预约上门交付。',
        showCancel: false
      });
      this.load();
    });
  }
});
