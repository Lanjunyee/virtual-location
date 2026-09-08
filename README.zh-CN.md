# 虚拟定位（个人版）

基于 Android-Mock-Location 的个人改造版：免 Root，通过系统开发者选项注入**带模拟标记**的定位。支持固定位置、书签、地图位置链接、有序途经点和 GPX 路线播放。

## 安装和使用

1. 从 `dist/virtual-location-aosp.apk` 开始。华为设备可另外试用 `dist/virtual-location-hms.apk`；HMS 版需要可用的 HMS Core。两个 APK 包名相同，同一时间安装一个，使用本次提供的 APK 可互相覆盖安装。
2. 打开手机开发者选项，将“选择模拟位置信息应用”设为“虚拟定位（个人版）”。授予精确定位权限，开启系统定位，并允许通知。
3. 固定位置页可输入 `纬度,经度`，也可点“在地图中选择”后轻触地图。例如 `31.2304,121.4737`。坐标必须为 **WGS84**，不能把高德／腾讯的 GCJ-02 或百度的 BD-09 坐标直接当成 WGS84。两点移动页的起点和终点也支持地图选择。
4. 菜单“路线模拟”打开路线页；每行输入一个途经点、通过系统文件选择器导入 GPX，或点“在地图中规划路线”依次添加途经点。地图提供撤销、清空和确认，仍按点选顺序直线连接，不沿道路自动规划。速度单位为 km/h，默认 8。点“开始路线”会替换当前模拟。
5. 暂停时持续提供当前位置；继续时从当前位置移动；到终点后保持终点。页面或通知栏的“停止”会释放模拟定位资源，恢复系统位置来源。真实定位的首个新结果仍需等待 GPS／网络定位更新，第三方也可能缓存旧位置。
6. 收藏通过原有书签菜单管理。地图分享沿用上游支持的 `geo:`、OsmAnd、OpenStreetMap 等位置链接；不是所有国内地图短链接或微信分享卡片都能解析。坐标系不明确时请手动输入已确认的 WGS84 坐标。

应用重启不会自动开始模拟；已经启动的前台服务可在离开界面后继续，直至手动停止或系统终止。重新打开界面不会自动重新播放路线。路线草稿和书签仅保存在本机，卸载会删除；GPX 原文件不会被修改。

内置地图使用中国大陆通常可直连的高德在线栅格瓦片，因此 AOSP 和 HMS 版都声明网络权限。地图显示时在大陆范围内将应用保存的 WGS84 坐标转换为 GCJ-02，点选后再转换回 WGS84；输入、GPX 和路线播放的数据格式不变。单点地图和空白路线优先以系统当前非模拟位置作为第一个选点，8 秒内无法获得时回退到原有或已保存位置；模拟定位运行中系统通常无法再提供真实位置。应用只加载用户当前浏览区域并保存在应用缓存目录，不提供区域预下载；离线时已缓存区域仍可显示，手动坐标、GPX 和路线播放不依赖地图网络。该瓦片接口没有 SLA，若用于公开发布或商业用途应改接高德官方 SDK 与应用 Key。地图组件仍固定为已归档的开源 `osmdroid 6.1.20`；HMS 版还携带华为 SDK 的网络及网络状态权限，HMS Core 的通信由华为组件管理。

## GPX 范围

- 接受 GPX 1.0／1.1 命名空间或无命名空间的文件；读取单个 `trk/trkseg/trkpt` 或单个 `rte/rtept`。
- 2～10000 个点、最大 2 MiB；拒绝 DTD、实体引用、畸形文件、非法经纬度、多轨迹／多分段和全重合路线。
- 时间戳、高程不决定移动过程；统一使用所填速度。途经点之间直线插值，不自动沿道路规划。长距离极区路线不适用。
- 速度必须大于 0 且不超过 360 km/h。示例文件见 `examples/local-route.gpx`。

## 支持范围

最低安装版本为 Android 4.4（API 19），实际验证情况见 `docs/COMPATIBILITY.md`。能装 APK 的鸿蒙需要实机核验；**原生鸿蒙 NEXT 不支持**。

学习通、微信和 MoveWell 尚无实机兼容性结果。系统提供模拟位置，不代表这些应用会显示或接受它，也不等于有效签到或运动记录。第三方接受记录的验证由用户手动完成。

## 构建

准备 JDK 17 或 21、Android SDK platform 35、build-tools 35.0.0、platform-tools。首次构建需要访问 Gradle、Google Maven、Maven Central 和华为 Maven 仓库。

```sh
export ANDROID_HOME=/absolute/path/to/android-sdk
./scripts/build.sh
```

脚本构建 AOSP 和 HMS 调试 APK，并运行两种变体的 Android Lint。Gradle Wrapper 固定 8.9，Android Gradle Plugin 固定 8.7.2，新增地图依赖固定为 `osmdroid 6.1.20`，其余依赖版本沿用固定的上游提交。输出到 `dist/`，其中两个 APK 已使用调试证书签名，可直接安装。

本次使用 Temurin JDK 21.0.11 和 Android SDK 35 构建。源码中保留上游其他模块与变体，但本次交付、修改测试范围只覆盖上述个人版变体。完整上游信息及修改记录见 `UPSTREAM.md`。

## 检查

不依赖第三方测试框架的 JVM 检查：

```sh
export KXML_JAR=/absolute/path/to/android-sdk/cmdline-tools/19/lib/external/net/sf/kxml/kxml2/2.3.0/kxml2-2.3.0.jar
./scripts/check.sh
```

Android SDK command-line tools 19 自带该解析器 JAR；它只供桌面测试，APK 使用 Android 自带的 XmlPullParser。
设备测试运行方法与结果见 `docs/COMPATIBILITY.md`。APK 签名、构建和设备测试记录在 `dist/verification/`，哈希在 `dist/SHA256SUMS.txt`。

## 许可证

项目沿用 GPL-2.0，见 `LICENSE.txt`。再分发修改版时保留许可证、原版权和修改记录，并按该许可证提供对应源码；osmdroid 使用 Apache-2.0，在线地图保留高德署名，HMS 等依赖各自保留其许可要求。原英文项目说明在 `README.md`。
