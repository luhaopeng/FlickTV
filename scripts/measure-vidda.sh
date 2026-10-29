#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-adb}"
PACKAGE="com.luhaopeng.flicktv.benchmark"
COMPONENT="$PACKAGE/com.luhaopeng.flicktv.MainActivity"
APK="$ROOT/app/build/outputs/apk/benchmark/app-benchmark.apk"
ITERATIONS="${ITERATIONS:-10}"
OUT_DIR="${OUT_DIR:-$ROOT/build/device-metrics}"
mkdir -p "$OUT_DIR"

if ! command -v "$ADB" >/dev/null 2>&1; then
  echo "未找到 adb；可通过 ADB=/path/to/adb 指定。" >&2
  exit 1
fi

DEVICE_COUNT="$($ADB devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"
if [[ "$DEVICE_COUNT" -ne 1 ]]; then
  echo "需要且仅允许连接一台可用 Android 设备，当前为 $DEVICE_COUNT 台。" >&2
  exit 1
fi

"$ROOT/gradlew" -p "$ROOT" :app:assembleBenchmark
$ADB install -r "$APK" >/dev/null

STAMP="$(date +%Y%m%d-%H%M%S)"
REPORT="$OUT_DIR/vidda-$STAMP.txt"
{
  echo "FlickTV Vidda 性能采样"
  echo "variant=benchmark (release-derived, R8/resource shrink enabled)"
  echo "package=$PACKAGE"
  echo "iterations=$ITERATIONS"
  echo "device=$($ADB shell getprop ro.product.manufacturer | tr -d '\r') $($ADB shell getprop ro.product.model | tr -d '\r')"
  echo "android=$($ADB shell getprop ro.build.version.release | tr -d '\r') sdk=$($ADB shell getprop ro.build.version.sdk | tr -d '\r')"
  echo
  echo "== 冷启动 am start -W =="
  for ((i=1; i<=ITERATIONS; i++)); do
    $ADB shell am force-stop "$PACKAGE"
    $ADB shell am start -W -n "$COMPONENT" | tr -d '\r'
    sleep 1
    if ! $ADB shell pidof "$PACKAGE" >/dev/null 2>&1; then
      echo "启动后进程未存活，请先检查 logcat；本次报告无效。" >&2
      exit 1
    fi
  done
  echo
  echo "== 内存 dumpsys meminfo =="
  $ADB shell dumpsys meminfo "$PACKAGE" | tr -d '\r'
  echo
  echo "== 帧统计 dumpsys gfxinfo =="
  $ADB shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null
  if [[ -t 0 ]]; then
    echo "请在设备上连续滚动首页约 30 秒，然后按回车采集 gfxinfo。" >&2
    read -r
  else
    echo "非交互模式：跳过人工等待并立即采集 gfxinfo。" >&2
  fi
  $ADB shell dumpsys gfxinfo "$PACKAGE" | tr -d '\r'
} > "$REPORT"

echo "性能报告已写入：$REPORT"
echo "注意：报告必须连同 variant、设备、Android 版本和迭代次数一起解读。"
