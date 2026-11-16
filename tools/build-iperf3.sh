#!/bin/bash
# 从 iperf-3.21 源码交叉编译 Android (arm64-v8a) 二进制，放入 app/src/main/assets/iperf3/
#
# 用法: tools/build-iperf3.sh [iperf源码目录]
# 默认源码目录: ~/Downloads/iperf-3.21
set -euo pipefail

SRC_DIR="${1:-$HOME/Downloads/iperf-3.21}"
NDK="${ANDROID_NDK_HOME:-/Volumes/CatCO3/Library/Android_SDK/ndk/27.0.12077973}"
API=31
TRIPLE=aarch64-linux-android
HOST_TAG=darwin-x86_64
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG"
# 产物必须命名成 lib*.so 打进 jniLibs（安装后释放到 nativeLibraryDir，应用内可执行）
OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/jniLibs/arm64-v8a"

[ -d "$SRC_DIR" ] || { echo "找不到 iperf 源码目录: $SRC_DIR"; exit 1; }
[ -d "$TOOLCHAIN" ] || { echo "找不到 NDK 工具链: $TOOLCHAIN"; exit 1; }

# 复制一份源码再构建，避免污染原始目录里的本机构建产物
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cp -R "$SRC_DIR" "$WORK/iperf-src"
cd "$WORK/iperf-src"
# 彻底清掉原目录带来的本机编译产物（Mach-O .o 混进 Android 链接会导致 undefined symbol）
make distclean >/dev/null 2>&1 || true
find . \( -name '*.o' -o -name '*.lo' -o -name '*.la' -o -name '*.a' \) -delete
rm -f src/iperf3

export CC="$TOOLCHAIN/bin/${TRIPLE}${API}-clang"
# 必须用 NDK 的 llvm-ar/llvm-ranlib，宿主机的 ar/ranlib 无法为 ELF 库建立符号索引
export AR="$TOOLCHAIN/bin/llvm-ar"
export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
export STRIP="$TOOLCHAIN/bin/llvm-strip"
./configure --host="$TRIPLE" --without-openssl --disable-shared --enable-static \
  --disable-dependency-tracking >configure.log
# 只构建 iperf3 本体，跳过 t_units 等测试程序
make -j"$(sysctl -n hw.ncpu)" -C src iperf3 >make.log

mkdir -p "$OUT_DIR"
cp src/iperf3 "$OUT_DIR/libiperf3.so"
"$TOOLCHAIN/bin/llvm-strip" "$OUT_DIR/libiperf3.so"
echo "完成: $OUT_DIR/libiperf3.so"
file "$OUT_DIR/libiperf3.so"
