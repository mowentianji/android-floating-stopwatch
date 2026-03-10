# Floating Stopwatch

一个最小可运行的 Android 悬浮秒表示例。

## 功能
- 请求悬浮窗权限
- 启动前台服务
- 显示可拖动悬浮窗
- 开始 / 暂停 / 继续 / 重置 / 关闭

## 环境
- Android Studio Hedgehog 及以上
- Android SDK 34
- Min SDK 26

## 本地运行
1. 用 Android Studio 打开本项目目录 `android-floating-stopwatch`
2. 等待 Gradle 同步完成
3. 运行到真机或模拟器
4. 首次打开时授予“在其他应用上层显示”权限
5. 点击 `启动悬浮秒表`

## GitHub Actions 自动打包 APK
这个项目已经带了工作流文件：`.github/workflows/android.yml`

用法：
1. 新建一个 GitHub 仓库
2. 把本项目推上去
3. 打开 GitHub 仓库的 `Actions`
4. 运行 `Build Android APK`，或者直接 push 触发
5. 构建完成后，在 `Artifacts` 下载 `floating-stopwatch-debug-apk`

## 版本规则
- 当前版本：`0.0.1`
- 每次后续修改都递增版本号，例如：`0.0.2`、`0.0.3`
- 同时递增 `versionCode`

## 说明
- 悬浮窗依赖 `SYSTEM_ALERT_WINDOW`
- 秒表通过前台服务持续运行，降低被系统回收的概率
- 当前自动产出的是 `debug APK`
- 如果你想继续，我可以再加：计次、吸边、主题色、通知栏快捷控制、release 签名打包
