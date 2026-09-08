#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to Android SDK with platform 35 and build-tools 35.0.0}"
sh android-studio-project/gradlew -p android-studio-project \
  :Mock-my-GPS:assembleEnglishWithAospLocationProvidersWithBackupRestoreSAFDebug \
  :Mock-my-GPS:assembleEnglishWithHuaweiMobileServicesFusedLocationProviderWithBackupRestoreSAFDebug \
  :Mock-my-GPS:lintEnglishWithAospLocationProvidersWithBackupRestoreSAFDebug \
  :Mock-my-GPS:lintEnglishWithHuaweiMobileServicesFusedLocationProviderWithBackupRestoreSAFDebug --console=plain
mkdir -p dist
cp android-studio-project/Mock-my-GPS/build/outputs/apk/englishWithAospLocationProvidersWithBackupRestoreSAF/debug/*.apk dist/virtual-location-aosp.apk
cp android-studio-project/Mock-my-GPS/build/outputs/apk/englishWithHuaweiMobileServicesFusedLocationProviderWithBackupRestoreSAF/debug/*.apk dist/virtual-location-hms.apk
