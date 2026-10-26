const BASE = 'https://120.26.19.193:8080'; // 自签证书，微信仅在调试模式下放行

App({
  globalData: {
    base: BASE,
    // 门店信息（详情页与预约结果页共用）
    shopName: '玉器门店',
    notice: '一物一码，图与货对应；标价请到店咨询'
  },

  onLaunch() {
    // 记录一次启动时间，供预约页计算最早可约时刻
    this.globalData.launchAt = Date.now();
  }
});
