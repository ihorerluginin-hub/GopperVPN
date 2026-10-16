#!/bin/bash

source ./env_java.sh || true
source ../buildScript/init/env_ndk.sh

BUILD=".build"

rm -rf $BUILD/android \
  $BUILD/java \
  $BUILD/javac-output \
  $BUILD/src

if [ -z "$GOPATH" ]; then
  GOPATH=$(go env GOPATH)
fi

# Гоппер VPN: какие архитектуры собирать.
#
# Форк NekoBox по умолчанию делает все четыре: arm, arm64, 386, amd64.
# Из-за этого в aar лежало 97,6 МБ распакованных .so, а APK весил
# 41,8 МБ, из которых 36 МБ — сжатые библиотеки ядра. Оставлены две
# архитектуры, которые покрывают реальные телефоны: arm64-v8a для
# всего, что выпущено после 2017 года, и armeabi-v7a для старых
# 32-битных. Эмуляторы x86/x86_64 после этого перестают ставиться.
#
# splits.abi в buildSrc выключен (universal APK), так что набор ABI
# определяется ровно тем, что попало в aar, — менять тут больше нечего.
#
# Переопределяется переменной окружения, если понадобится полный набор:
#   LIBCORE_TARGET=android/arm,android/arm64,android/386,android/amd64 ./build.sh
TARGET="${LIBCORE_TARGET:-android/arm,android/arm64}"

export GOBIND=gobind-matsuri
# with_awg включает исходящий AmneziaWG (protocol/awg). Без этого тега
# он регистрируется как заглушка и выдаёт понятную ошибку.
"$GOPATH"/bin/gomobile-matsuri bind -v -target="$TARGET" -androidapi 21 -cache "$(realpath $BUILD)" -trimpath -ldflags='-s -w' -tags='with_conntrack,with_gvisor,with_quic,with_wireguard,with_awg,with_utls,with_clash_api' . || exit 1
rm -r libcore-sources.jar

echo ">> собраны архитектуры: $TARGET"

proj=../app/libs
mkdir -p $proj
cp -f libcore.aar $proj
echo ">> install $(realpath $proj)/libcore.aar"
