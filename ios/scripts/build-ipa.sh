#!/bin/sh
# builds an unsigned Mochy.ipa that sidestore or altstore signs and installs on the phone
set -e
cd "$(dirname "$0")/.."

CONFIG="${1:-Release}"
rm -rf build/Payload build/Mochy.ipa

xcodebuild -project Mochy.xcodeproj -target Mochy -configuration "$CONFIG" -sdk iphoneos \
    SYMROOT="$PWD/build" CODE_SIGNING_ALLOWED=NO -quiet build

mkdir -p build/Payload
cp -R "build/$CONFIG-iphoneos/Mochy.app" build/Payload/
(cd build && zip -qry Mochy.ipa Payload)
echo "built $PWD/build/Mochy.ipa"
