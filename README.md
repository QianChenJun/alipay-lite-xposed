# 支付宝极速模式

[![Build APK](https://github.com/QianChenJun/alipay-lite-xposed/actions/workflows/build.yml/badge.svg)](https://github.com/QianChenJun/alipay-lite-xposed/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![libxposed API](https://img.shields.io/badge/libxposed-API%20102-brightgreen.svg)](https://github.com/libxposed/api)

一个 LSPosed 模块：通过支付宝自身的降级 API，让它稳定走官方低端机路径，启用「极速启动」。

## 背景

支付宝 **设置 → 通用 → 极速模式** 的官方说明是：

> 设备运行较慢时，将自动收起首页推荐并减少后台服务，加快页面浏览。

也就是说它是**自动**的，没有手动开关；手机性能足够时不会开启。

本模块不伪造硬件，只做两件事：

1. 把支付宝自己的极速模式标记 `jisu_pattern_state_key` 钉成 `"T"`；
2. 调用支付宝自带的 `TaskDegradeLowDeviceUtil.mockToLowPhone()` 写入低端机状态。

## 做的事

进程启动时（所有进程）：

- `TaskGradeController.getJiSuPatternValue()` → `true`
- `TaskGradeController.isJiSuPatternSwitchOpen()` → `true`
- `TaskGradeController.updateJiSuPatternSwitch(boolean)` 入参强制 `true`
- `TaskDegradeLowDeviceUtil.isLowDevice()` → `true`
- `TaskDegradeLowDeviceUtil.getDeviceGradeInfo()` → `"T"`
- `TaskDegradeLowDeviceUtil.getDeviceScore()` → `0`
- `NewDegradeController.isLowDevice()` / `readLowDeviceFlag()` → `true`

主进程启动时（`ContextWrapper.attachBaseContext` / `Application.onCreate`）：

- `jisu_pattern_state_key = "T"`
- `TaskDegradeLowDeviceUtil.mockToLowPhone(defaultSharedPreferences)`

每个 hook 独立 `try/catch`：支付宝升级后类名或方法签名变了，只会在 logcat 记一行 `missing`，不会让整个模块崩掉。

## 为什么不用伪造机型

- 伪造 `Build.MODEL`、内存、温度会被 `antsp` 的设备指纹交叉校验，"Build 说是旗舰机、硬件特征却不是"本身就是异常信号。
- 伪造成旗舰机还会让支付宝认为设备很强，反而**关掉**低端机优化，和目标相反。
- 全局 hook `SharedPreferences` 会让进程内每次配置读取都过一次拦截，是净性能损失。

本模块只动降级相关的布尔值和分数，不碰安全组件、不碰硬件读数。

## 兼容性

| 项 | 值 |
|---|---|
| 支付宝 | `10.8.76.8100`（versionCode `4070`）实测 |
| LSPosed | 2.x（libxposed API 102） |
| Android | 9+（minSdk 26） |
| 包名 | `io.github.qianchenjun.alipaylite` |

> 支付宝版本升级后类名可能变化。日志里出现 `method missing` 时按提示补规则即可。

## 安装

从 [Releases](https://github.com/QianChenJun/alipay-lite-xposed/releases) 下载 APK，或本地构建。

```powershell
adb install -r alipay-lite-xposed-1.0.0.apk
```

模块**没有桌面图标**，只能从 LSPosed 进入：

1. LSPosed → 模块 → 支付宝极速模式
2. 启用模块
3. 强行停止支付宝再重新打开

作用域已锁定为支付宝（静态作用域），无需手动勾选，也无法添加到其他应用。

## 验证

```powershell
adb logcat -c
adb shell am force-stop com.eg.android.AlipayGphone
adb shell monkey -p com.eg.android.AlipayGphone -c android.intent.category.LAUNCHER 1
adb logcat -d | grep AlipayLite
```

正常应看到：

```
AlipayLite: hooked ...TaskGradeController.getJiSuPatternValue x1
AlipayLite: pin jisu_pattern_state_key=T
AlipayLite: applied TaskDegradeLowDeviceUtil.mockToLowPhone
AlipayLite: called ...TaskGradeController.getJiSuPatternValue -> true
AlipayLite: called ...TaskGradeController.isJiSuPatternSwitchOpen -> true
AlipayLite: called ...TaskDegradeLowDeviceUtil.isLowDevice -> true
```

`hooked` 只表示钩子装上了；`called ... -> true` 才表示**支付宝真的查询了这个开关并拿到了 true**。

界面上的直接证据：支付宝首页底部显示「已开启极速模式」，首页推荐变成按需展开的「加载首页栏目」。

## 构建

```bash
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。Xposed API 是 `compileOnly`，不会打进 APK。

CI 在每次 push 到 `main` 时自动构建，产物见 [Actions](https://github.com/QianChenJun/alipay-lite-xposed/actions)。

## 回滚

1. LSPosed 里关掉模块，强行停止支付宝。
2. 用 root 终端运行 [`tools/restore_lowend.sh`](tools/restore_lowend.sh)，把持久化的低端机标记改回去。
3. 重新打开支付宝。

只关模块**不会**自动还原 `mockToLowPhone` 写下的标记——那些值在支付宝默认 SP 里（`com.eg.android.AlipayGphone_preferences.xml`），所以需要脚本或清除支付宝数据。

## 已知限制

- 极速模式会改变首页加载策略（推荐栏目变成按需展开）。如果其他自动化流程依赖这些入口，需要自行验证。
- `staticScope` 与现代 API 需要 LSPosed 2.x。
- 模块依赖支付宝内部类名，属于版本敏感实现，升级后可能需要适配。

## 开发

- 入口：`META-INF/xposed/java_init.list`
- 作用域：`META-INF/xposed/scope.list` + `module.prop` 的 `staticScope=true`
- 主要代码：`app/src/main/java/io/github/qianchenjun/alipaylite/MainHook.java`

## License

[Apache-2.0](LICENSE)
