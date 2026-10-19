# Changelog

## 1.0.1

- 统一 Java namespace 为 `io.github.qianchenjun.alipaylite`
- 固定 release 签名：CI 通过 GitHub Secrets 注入 keystore，后续版本可覆盖升级
- 补充 CHANGELOG 与 issue 模板

## 1.0.0

- 首个版本：钉住支付宝官方极速模式开关 `jisu_pattern_state_key`
- 调用支付宝自带的 `TaskDegradeLowDeviceUtil.mockToLowPhone()`
- 静态作用域，仅作用于 `com.eg.android.AlipayGphone`
- 无桌面图标，仅从 LSPosed 管理
