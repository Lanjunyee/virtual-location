# UI 美化验证

验证环境：Android 15 / API 35 arm64 模拟器，AOSP 变体，中文；视口 360 × 800dp，横屏 800 × 360dp，字体倍率 1.0 与 1.3。

## 检查结果

- JVM 路线／GPX 检查：49 项通过。
- AOSP 和 HMS 调试 APK 构建及 Android Lint：通过；未将上游既有警告视为已修复。
- 定位与页面重建回归：19 项通过，覆盖输入／光标恢复、固定定位、路线前进、暂停／继续、后台保持、停止恢复、终点保持、权限撤销和通知清理。
- UI 自动回归：书签长名称及高精度坐标、新增／修改／删除及空状态、设置保存与重开、GPX 导入回调、地图清空／点选／撤销／确认与迟到权限回调均通过。
- 实际页面检查：固定位置、两点移动、路线和控制区、在线地图及署名、书签和设置。固定位置、路线、地图和设置另检查大字体；路线检查软键盘，路线、地图与设置检查横屏及操作区滚动。

GPX 自动回归通过拦截系统文件选择器并返回测试文件验证导入回调；另已通过系统 Downloads 文件选择器实际导入三点示例 GPX。HMS 本轮只完成构建与静态检查，没有华为真机结果；API 19 的最低安装兼容性由构建和 Lint 检查，未运行 API 19 设备。

## 重跑

使用专用、书签为空的测试模拟器；UI 测试会新建并删除测试书签，将定位更新间隔恢复为 1000ms，并写入路线测试草稿。先按 `COMPATIBILITY.md` 构建、安装 APK 并授权，然后运行：

```sh
adb shell am instrument -w com.github.warren_bank.mock_location.personal.test/com.github.warren_bank.mock_location.DeviceCheck
adb shell am instrument -w -e ui true com.github.warren_bank.mock_location.personal.test/com.github.warren_bank.mock_location.DeviceCheck
```

必须检查输出中的 PASS 与 FAIL，不能只看命令退出码。UI 回归使用 Android 原生 Instrumentation 和无障碍控件接口，无第三方测试依赖。

## 产物

- APK：`dist/virtual-location-aosp.apk`、`dist/virtual-location-hms.apk`；校验文件：`dist/SHA256SUMS.txt`。
- 构建、设备和 UI 回归日志：`dist/verification/ui/`。
- 实际截图：`dist/verification/ui/screenshots/`。

模拟器中原有应用使用另一份本机调试证书。为保留其数据，本轮仅将测试安装副本重新签名后覆盖安装；交付 APK 保持工作区 `.android-user/debug.keystore` 签名，未改动签名配置。
