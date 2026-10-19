# 对接 LSPosed 模块仓库

目标：[modules.lsposed.org](https://modules.lsposed.org/) 收录本模块。

## 前置条件

1. **applicationId 必须匹配 GitHub 用户名命名空间**。提交机器人会把用户名 `QianChenJun` 归一化为 `qianchenjun`，自动审核只接受 `io.github.qianchenjun.*`。当前包名已改为 `io.github.qianchenjun.alipaylite`。
2. 仓库需要公开（提交时管理员会看源码与 Release）。
3. 至少有一个可下载的 APK Release。

## 提交步骤

1. 把本仓库设为 public。
2. 打 tag 并发布 APK：

   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

   `Release` workflow 会自动构建并创建 GitHub Release。

3. 在 [Xposed-Modules-Repo/submission](https://github.com/Xposed-Modules-Repo/submission/issues/new) 新建 issue：

   - 标题：`[submission] io.github.qianchenjun.alipaylite`
   - 标签：`submission`
   - 正文：

   ```markdown
   ## Module information

   - Display name: 支付宝极速模式
   - Application ID: `io.github.qianchenjun.alipaylite`
   - Maintainer: @QianChenJun
   - Source: https://github.com/QianChenJun/alipay-lite-xposed
   - License: Apache-2.0
   - Xposed API: libxposed 102 (static scope: `com.eg.android.AlipayGphone`)
   - Current version: `1.0.0` (versionCode `1`)
   - Release: https://github.com/QianChenJun/alipay-lite-xposed/releases/tag/v1.0.0
   - APK SHA-256: `<填入 Release APK 的 sha256>`

   ## Functionality

   Forces Alipay to use its own low-end degrade path by pinning
   `TaskGradeController.jisu_pattern_state_key` and calling the app's own
   `TaskDegradeLowDeviceUtil.mockToLowPhone()`. No hardware spoofing, no global
   SharedPreferences hook, no security-component writes.

   ## Compatibility

   - Alipay `10.8.76.8100` (versionCode 4070) verified
   - LSPosed 2.x / Android 9+
   - Static scope: `com.eg.android.AlipayGphone` only

   ## After approval

   I will publish releases to the assigned
   `Xposed-Modules-Repo/io.github.qianchenjun.alipaylite` repository using the
   required `<versionCode>-<versionName>` tag format.
   ```

4. 等待 `approved` 标签。机器人会创建 `Xposed-Modules-Repo/io.github.qianchenjun.alipaylite` 并给维护者写权限。

## 审核通过后的发布格式

参考已收录模块的结构：

```
Xposed-Modules-Repo/io.github.qianchenjun.alipaylite/
├── README.md
├── SUMMARY
└── alipay-lite-xposed-1.0.0.apk
```

Release：

- tag：`1-1.0.0`（`<versionCode>-<versionName>`）
- title：`1.0.0`
- asset：APK

站点按 GitHub Release 聚合，更新模块只需要在该仓库发新 Release。

## 如果不想改包名

保留一个不属于 `io.github.qianchenjun.*` 的包名也可以，但需要满足下面之一：

- 在 `tstar.com` 的根域加 TXT 记录 `lsposed-modules-repo-verification=QianChenJun`（适用于拥有该域名的情况）；
- 走人工申诉，在 issue 里说明包名来源与既有安装的迁移成本。

自动审核路径更简单，所以当前选择了 `io.github.qianchenjun.alipaylite`。
