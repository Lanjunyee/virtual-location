# 验证与兼容性

验证日期：2026-09-08。上游提交见 `UPSTREAM.md`。

## 已完成检查

| 项目 | 结果 |
|---|---|
| 固定上游原版 AOSP 构建 | 通过；首次缺 SDK，安装后以未修改源码重新构建成功 |
| 个人版 AOSP／HMS APK 构建与签名验证 | 通过；minSdk 19、targetSdk 35，两个包使用相同调试证书 |
| 两变体 Android Lint | 0 错误；保留旧界面、弃用接口、翻译和 SDK 等警告，详见原始报告 |
| 路线／GPX JVM 检查 | 43 项通过，包含地图草稿解析和 WGS84／GCJ-02 往返误差检查 |
| AOSP Android 15（API 35）arm64 模拟器 | 17 项设备检查通过 |
| 中文界面及系统文件选择器 | 已检查；成功从 Downloads 导入示例 GPX 的 3 个点 |
| 大陆直连地图选择 | 高德在线瓦片、真实位置初始化、WGS84 回填及高德署名通过；固定位置、起点、终点、多点路线、旋转恢复、取消、撤销、清空、少于两点拒绝及断网缓存已检查 |
| 通知栏停止按钮 | 已点击验证，服务停止、界面恢复“已停止” |

设备检查覆盖：固定坐标、模拟标记、静止速度、路线速度和前进、暂停位置保持、继续、后台服务、停止清理、真实 GPS 恢复、终点坐标／速度及状态、销毁清理、Android 原生 GPX 解析、DTD 拒绝、重新启动、模拟授权撤销、通知清理。地图交互另以模拟器人工操作验证，不将瓦片服务可用性视为定位服务依赖。

模拟器原生 GNSS 的位置输入用于验证恢复；这不等于真实手机的卫星接收测试。设备检查通过的是 AOSP 变体，不能推断 HMS 定位链路已验证。

## 目标兼容性矩阵

| 环境／目标 | 系统收到模拟位置 | 目标应用显示位置 | 目标应用接受记录 |
|---|---|---|---|
| Android 15 arm64 模拟器，AOSP 版测试监听器 | 已验证 | 测试监听器已验证 | 不适用 |
| 其他安卓版本及真机 | 未验证 | 未验证 | 未验证 |
| 可安装 APK 的鸿蒙，AOSP 版 | 未验证 | 未验证 | 未验证 |
| 华为真机／HMS Core，HMS 版 | 未验证 | 未验证 | 未验证 |
| 学习通（版本待提供） | 待对应实机验证 | 未验证 | 未验证 |
| 微信小程序 MoveWell（微信与小程序版本待提供） | 待对应实机验证 | 未验证 | 未验证 |
| 原生鸿蒙 NEXT | 不支持本版 APK | 不适用 | 不适用 |

最低 SDK 只是安装门槛，不是已经验证所有 Android 4.4 及以后设备。HMS 版包含上游固定版本的原生 SDK；不同华为系统、HMS Core 版本及 16 KiB 内存页设备仍需实测。

## 重跑设备检查

只在用于测试的模拟器／手机上执行。命令为本应用授予测试权限、选择模拟定位应用；测试中会临时撤销并恢复此授权，并改变系统位置。

先启动 API 35 的测试模拟器、开启系统定位，再执行（`adb` 和 SDK 工具需在 PATH 中）：

```sh
sh android-studio-project/gradlew -p android-studio-project \
  :Mock-my-GPS:assembleEnglishWithAospLocationProvidersWithBackupRestoreSAFDebugAndroidTest
adb install -r dist/virtual-location-aosp.apk
adb install -r android-studio-project/Mock-my-GPS/build/outputs/apk/androidTest/englishWithAospLocationProvidersWithBackupRestoreSAF/debug/Mock-my-GPS-english-withAospLocationProviders-withBackupRestoreSAF-debug-androidTest.apk
adb shell pm grant com.github.warren_bank.mock_location.personal android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.github.warren_bank.mock_location.personal android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant com.github.warren_bank.mock_location.personal android.permission.POST_NOTIFICATIONS
adb shell appops set com.github.warren_bank.mock_location.personal android:mock_location allow
adb emu geo fix 121.49 31.24
adb shell am instrument -w com.github.warren_bank.mock_location.personal.test/com.github.warren_bank.mock_location.DeviceCheck
```

检查输出必须包含全部 `PASS` 且没有 `FAIL`；不能只根据 `adb` 的进程退出码判断。测试结束会停止服务。异常中断后可手动停止应用并重新选择模拟定位应用。

## 后续实机步骤

记录手机型号、完整系统版本、应用版本和 HMS Core 版本。依次验证固定位置、十分钟路线、锁屏、暂停／继续、通知停止、权限撤销和重新启动。目标应用的“显示位置”与“接受记录”分别记录；实际提交签到或运动记录由用户手动完成。

原始构建、静态检查、签名信息及测试输出在 `dist/verification/`，不将未验证目标标为兼容。
