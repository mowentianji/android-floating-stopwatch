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

## 运行
1. 用 Android Studio 打开本项目目录 `android-floating-stopwatch`
2. 等待 Gradle 同步完成
3. 运行到真机或模拟器
4. 首次打开时授予“在其他应用上层显示”权限
5. 点击 `启动悬浮秒表`

## 说明
- 悬浮窗依赖 `SYSTEM_ALERT_WINDOW`
- 秒表通过前台服务持续运行，降低被系统回收的概率
- 如果你想继续，我可以再加：计次、吸边、主题色、通知栏快捷控制
