# Virtual Location (Personal Edition)

> A no-root Android mock-location app: fixed position, two-point trips, multi-waypoint route playback, GPX import, and full-screen map picking.
> 免 Root 的 Android 虚拟定位工具，中文说明见 [README.zh-CN.md](README.zh-CN.md)。

A personal fork of the open-source project [warren-bank/Android-Mock-Location](https://github.com/warren-bank/Android-Mock-Location) (`service` branch, pinned at commit `142eb1b`, v02.05.03), released under the **GPL-2.0** license. The upstream commit and the full record of modifications are documented in [`UPSTREAM.md`](UPSTREAM.md).

## Features

- **No-root mock location**: injects mock-flagged coordinates via the system developer option "Select mock location app", covering GPS / Network / Fused location providers; no root required, no need to install as a system app.
- **Fixed position**: enter `latitude,longitude` (WGS84) manually or pick on the map; bookmark management supported.
- **Two-point trip**: set an origin, a destination, and a duration; moves along a straight-line interpolation over time.
- **Multi-waypoint route simulation**: ordered waypoints, playback at a uniform speed (km/h, default 8), pause / resume, hold at destination, and local draft saving.
- **GPX route import**: supports GPX 1.0 / 1.1 (single track or single route), 2–10,000 points, max 2 MiB; strict validation that rejects DTDs, entity references, malformed coordinates, and invalid files.
- **Full-screen map picking**: built-in osmdroid 6.1.20 with AMap online tiles (directly reachable in mainland China), with undo, clear, and rotation restore; within mainland China, display automatically converts WGS84 ↔ GCJ-02, while all inputs and outputs remain WGS84.
- **Location-sharing integration**: handles `geo:` links and location Intents from OsmAnd, OpenStreetMap, and similar apps.
- **Foreground service + notification stop**: keeps mocking after leaving the screen; one tap in the notification stops it and releases the system location providers.
- **Dual-variant APKs**: an AOSP build and a Huawei HMS build (requires HMS Core); same package name, so they can silently replace each other.
- Chinese UI with a clean light theme.

## Requirements

- Android 4.4 (API 19) or later; native HarmonyOS NEXT is not supported.
- After installing, set "Select mock location app" in Developer options to this app, grant precise location permission, and enable system location.

## Building

Environment: JDK 17 or 21, Android SDK platform 35, build-tools 35.0.0, platform-tools.

```sh
export ANDROID_HOME=/absolute/path/to/android-sdk
./scripts/build.sh        # builds AOSP + HMS debug APKs and runs Lint for both variants, output in dist/
./scripts/check.sh        # dependency-free JVM checks (needs KXML_JAR, see README.zh-CN.md)
```

Gradle Wrapper 8.9, AGP 8.7.2, osmdroid 6.1.20; repositories are Maven Central + Huawei Maven. APKs are signed with a debug certificate for personal installation; manage your own release signing before publishing.

## Verification

- 49 JVM checks pass (interpolation, antimeridian-crossing coordinates, GPX parsing and security rejections, WGS84 / GCJ-02 round-trip error).
- 17 on-device checks pass on an Android 15 (API 35) arm64 emulator; Lint reports 0 errors for both variants.
- Map interactions, GPX import, and the notification stop action were verified manually on the emulator.
- Real-device and third-party-app compatibility is **not verified**; it is recorded honestly in [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md), and unverified targets are never labeled as compatible.

## Repository Layout

| Directory | Contents |
| --- | --- |
| `android-studio-project/` | App source code (upstream multi-module, multi-flavor structure) |
| `scripts/` | Build and check scripts |
| `tests/` | Dependency-free JVM checks |
| `docs/` | Compatibility and UI verification records |
| `dist/` | Build artifacts, SHA-256 checksums, and verification material (not committed) |
| `examples/` | Sample GPX route |

## Notes

- Coordinates must be **WGS84**; GCJ-02 (AMap / Tencent) or BD-09 (Baidu) coordinates cannot be used directly.
- The built-in map uses AMap online raster tiles (no SLA), for personal use only; for public release or commercial use, switch to the official AMap SDK with your own key.
- The app only mocks system location: whether a third-party app displays or accepts the mocked location depends on that app and must be verified by the user.
- This project is intended for legitimate use cases such as development, debugging, and testing. Comply with local laws and the terms of service of target apps; do not use it to fake check-ins, workout records, or similar.

## License

- Project code: GPL-2.0 (inherited from upstream, see [`LICENSE.txt`](LICENSE.txt)); keep the original copyright and modification records and provide the corresponding source code when redistributing.
- osmdroid: Apache-2.0; online maps retain the AMap attribution; HMS and other dependencies retain their own license terms.

## Acknowledgements

- Upstream project: [warren-bank/Android-Mock-Location](https://github.com/warren-bank/Android-Mock-Location) (its design combines [FakeTraveler](https://github.com/mcastillof/FakeTraveler) and [FakeGPS](https://github.com/xiangtailiang/FakeGPS))
- Map component: [osmdroid](https://github.com/osmdroid/osmdroid)
