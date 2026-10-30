# FlickTV

FlickTV 是一个面向 Android TV 与投影设备的原生影视聚合客户端，使用 Kotlin、Jetpack Compose 和 ExoPlayer 构建，不使用 WebView。

> 本项目仅用于个人学习、技术研究与访问你有权使用的内容源。项目本身不提供、存储或分发影视内容，也不包含绕过 DRM、付费墙或访问控制的能力。使用者应自行确认数据源授权，并遵守内容提供方条款及所在地法律法规。

## 功能

- 苹果 CMS 多源并行搜索，每个源独立超时与失败隔离
- 影片名规范化后进行同名多源聚合
- 源健康度持久化管理：连续失败自动禁用、阶梯退避恢复、手动探测
- LibreTV 风格的电影/剧集推荐分类与本地缓存回退
- 单源分类浏览与分页
- HLS 播放、保守型 m3u8 广告分片过滤
- 播放器按内存情况向前缓冲，目标上限约 90 秒；弱网下延长清单/分片超时并自动重试
- 保留约 15 秒后退缓存，修复带宽估算透传以支持 HLS 自适应降码率
- 自动连播；已有播放进度的分集再次打开时自动续播
- 毫秒级进度存储、播放历史与跨源收藏
- 详情页分集展示本地观看进度
- Android TV 遥控器 D-pad 焦点导航
- 远程 JSON 源配置更新与内置默认配置回退

## 技术栈

- Kotlin 1.5.31
- Jetpack Compose 1.0.5
- Android Gradle Plugin 7.4.2 / Gradle 7.5
- Retrofit 2 + OkHttp 4 + Gson
- Coil Compose
- ExoPlayer 2.16.1（HLS + 自定义 DataSource）
- Room 2.3.0 + SharedPreferences
- Kotlin Coroutines / Flow

## 系统要求

- `minSdk 21`
- `compileSdk 30`
- `targetSdk 30`
- Android TV / Android 11 设备为主要目标环境
- JDK 11 或 17
- Android SDK Platform 30
- Android Build Tools 30.0.3

应用包名：

```text
com.luhaopeng.flicktv
```

该包名与早期开发包不同，因此 Android 会将它视为独立应用；它不能覆盖旧包，也不会自动继承旧包私有目录中的播放历史、收藏和续播进度。

## 构建

1. 安装 JDK 11 或 17，以及 Android SDK Platform 30。
2. 在项目根目录创建 `local.properties`：

```properties
sdk.dir=/path/to/Android/sdk
```

3. 执行：

```bash
./gradlew clean lintDebug testDebugUnitTest assembleDebug
```

调试 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release 构建（启用 R8 与资源收缩）：

```bash
./gradlew assembleRelease
```

当前仓库不包含 release keystore，因此 `release` APK 默认不签名。请在自己的环境中配置签名后再分发。

性能测量使用独立的 `benchmark` 变体：它继承 release 的 R8/资源收缩配置，以 debug key 签名，并追加 `.benchmark` 包名后缀，不会覆盖日常安装的 FlickTV。

```bash
./gradlew assembleBenchmark
```

可安装 APK：

```text
app/build/outputs/apk/benchmark/app-benchmark.apk
```

## 安装到 Android TV / 投影仪

开启设备的开发者选项与 ADB 调试，并确保电脑和设备在同一网络：

```bash
adb connect <设备IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

如果设备通过 USB 连接，可跳过 `adb connect`。

## 遥控器操作

- D-pad：移动焦点
- OK / Enter：确认、播放或暂停
- 播放页左 / 右：每次后退或前进 5 秒，连续按键会累积目标位置
- 播放页下：控制条显示时聚焦“下一集”
- 返回：关闭软键盘、关闭弹窗或返回上一级页面

## 数据源配置

内置源位于：

```text
app/src/main/assets/default_sources.json
```

远程配置格式：

```json
{
  "sources": [
    {
      "id": "demo",
      "name": "示例源",
      "api": "https://example.com/api.php/provide/vod"
    }
  ]
}
```

将 JSON 托管到可公开读取的 HTTP(S) 地址，在 App 设置页填写原始 JSON URL 并选择“立即更新”。远程更新成功后会写入应用内部缓存；更新失败时继续使用已有缓存，缓存不可用时回退到内置默认配置。

海报网格固定为 5 列，不接受远程配置修改。

## 源健康度逻辑

- 搜索、分类、详情等请求成功：清零连续失败次数并更新时间
- 连续失败 3 次：自动禁用该源
- 自动重试阶梯：10 / 30 / 60 / 120 / 360 / 720 分钟
- 到达重试时间后，在下一次业务请求中重新放行探测
- 设置页可忽略退避时间立即探测
- 健康数据通过 SharedPreferences 持久化

## 目录

```text
app/src/main/java/com/luhaopeng/flicktv/
├── data/
│   ├── adblock/        # m3u8 清洗与 DataSource 拦截
│   ├── api/            # 苹果 CMS Retrofit API 与网络配置
│   ├── config/         # 默认/缓存/远程配置
│   ├── db/             # Room 与播放进度存储
│   ├── health/         # 源健康状态机
│   ├── merge/          # 多源同名聚合
│   ├── model/          # 业务数据模型与播放地址解析
│   ├── recommendation/ # 推荐请求与缓存
│   └── repository/     # 多源业务仓库
├── state/              # 可取消业务请求槽位
└── ui/
    ├── components/     # TV 通用焦点与海报组件
    ├── focus/          # 单焦点网格游标与按键周期规则
    ├── detail/
    ├── history/
    ├── home/
    ├── player/
    ├── search/
    ├── settings/
    └── theme/
```

## 测试与检查

```bash
./gradlew lintDebug testDebugUnitTest
```

当前 JVM 测试覆盖多源聚合、推荐标签、配置兼容、播放串解析、m3u8 过滤、播放网络超时隔离、异步结果身份、TV 网格游标、按键周期、播放器阶段状态、连续 seek、续播位置与分集观看进度，共 51 个测试。Android TV 遥控焦点仍需结合真机回归。

## Vidda 真机性能基线

Compose 性能必须在真实设备的 release-derived 变体上测量；Debug 和模拟器数据只用于诊断，不作为结论。连接且仅连接一台 Vidda 后运行：

```bash
ITERATIONS=10 ./scripts/measure-vidda.sh
```

脚本会构建并安装 `benchmark` 变体，记录冷启动 `am start -W`、内存 `dumpsys meminfo` 和交互后的 `dumpsys gfxinfo`。报告写入 `build/device-metrics/`，每份报告包含变体、设备型号、Android/SDK 版本和迭代次数。

## 已知限制

- 苹果 CMS 站点可能随时失效、限流或改变响应格式。
- 豆瓣接口、公共代理与海报镜像可能不可用；App 会优先使用分类缓存。
- HLS 广告没有统一标准，过滤策略刻意保守，不能保证识别所有广告。
- 某些资源需要特殊 Referer、Cookie、地区权限或 DRM；本项目不处理 DRM，也不绕过访问控制。
- Compose 1.0.5 的 TV 焦点行为较旧，项目对方向键按键周期和 Lazy 容器采用显式兼容逻辑。

## 参考

- [LibreTV](https://github.com/LibreSpark/LibreTV)：业务体验与推荐分类的参考项目
- [Android TV](https://developer.android.com/tv)
- [ExoPlayer](https://github.com/google/ExoPlayer)

FlickTV 是独立的原生 Android TV 重写实现，不包含 LibreTV 源代码。

## License

本项目以 [GNU General Public License v3.0](LICENSE) 发布。
