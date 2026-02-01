#!/system/bin/sh
# AzLauncher Root Kiosk Setup for Android 12 STB/TV
# Run via: adb shell su -c 'sh /sdcard/stb_root_kiosk_setup.sh apply'
# Or copy this file to device and run as root.

APP_PKG="com.azka.launcher"
APP_HOME_ACTIVITY=".MainActivity"

log() { echo "[AzLauncherKiosk] $*"; }

pkg_exists() {
  pm list packages | grep -q "^package:$1$"
}

disable_if_exists() {
  PKG="$1"
  if pkg_exists "$PKG"; then
    log "Disabling: $PKG"
    pm disable-user --user 0 "$PKG" >/dev/null 2>&1 || pm disable "$PKG" >/dev/null 2>&1
  else
    log "Skip (not found): $PKG"
  fi
}

enable_if_exists() {
  PKG="$1"
  if pkg_exists "$PKG"; then
    log "Enabling: $PKG"
    pm enable "$PKG" >/dev/null 2>&1
  else
    log "Skip (not found): $PKG"
  fi
}

set_home() {
  # Android 12 biasanya punya: cmd package set-home-activity
  # Kita coba beberapa format supaya kompatibel.
  log "Setting HOME to $APP_PKG/$APP_HOME_ACTIVITY"
  cmd package set-home-activity "$APP_PKG/$APP_HOME_ACTIVITY" >/dev/null 2>&1 && return 0
  cmd package set-home-activity "$APP_PKG/$APP_PKG$APP_HOME_ACTIVITY" >/dev/null 2>&1 && return 0
  cmd package set-home-activity "$APP_PKG/$APP_PKG.MainActivity" >/dev/null 2>&1 && return 0

  # Fallback: buka chooser HOME sekali (kalau cmd tidak ada)
  log "WARN: cmd package set-home-activity not available. You may need to set Home manually once."
  return 1
}

clear_home_defaults_for_others() {
  # Buang default HOME launcher lain biar sistem nanya lagi / tidak balik ke launcher bawaan
  # (Tidak semua device punya semua paket ini, aman kalau skip)
  for PKG in \
    com.google.android.tvlauncher \
    com.google.android.apps.tv.launcherx \
    com.android.tv.launcher \
    com.google.android.leanbacklauncher \
    com.android.launcher3 \
    com.google.android.apps.nexuslauncher
  do
    if pkg_exists "$PKG"; then
      log "Clearing defaults: $PKG"
      pm clear "$PKG" >/dev/null 2>&1
    fi
  done
}

apply() {
  log "=== APPLY KIOSK (root) ==="

  # 1) Clear defaults launcher lain
  clear_home_defaults_for_others

  # 2) Set AzLauncher as HOME (paksa)
  set_home

  # 3) Disable launcher bawaan (pilih aman: hanya launcher, bukan SystemUI)
  #    Tambah/kurangi daftar sesuai STB kamu nanti.
  disable_if_exists "com.google.android.tvlauncher"
  disable_if_exists "com.google.android.apps.tv.launcherx"
  disable_if_exists "com.android.tv.launcher"
  disable_if_exists "com.google.android.leanbacklauncher"
  disable_if_exists "com.android.launcher3"

  # 4) Batasi Settings (opsional tapi direkomendasikan untuk guest house)
  #    Ini yang paling sering ada di Android TV:
  disable_if_exists "com.android.tv.settings"
  #    Ini untuk AOSP/handset:
  disable_if_exists "com.android.settings"

  log "Done. Reboot recommended."
  log "Reboot: adb shell su -c reboot"
}

rollback() {
  log "=== ROLLBACK KIOSK (root) ==="

  # Enable kembali settings & launcher umum
  enable_if_exists "com.android.tv.settings"
  enable_if_exists "com.android.settings"

  enable_if_exists "com.google.android.tvlauncher"
  enable_if_exists "com.google.android.apps.tv.launcherx"
  enable_if_exists "com.android.tv.launcher"
  enable_if_exists "com.google.android.leanbacklauncher"
  enable_if_exists "com.android.launcher3"

  log "Rollback done. You may need to set Home again in Settings."
}

case "$1" in
  apply) apply ;;
  rollback) rollback ;;
  *)
    echo "Usage:"
    echo "  su -c 'sh $0 apply'"
    echo "  su -c 'sh $0 rollback'"
    exit 1
    ;;
esac
