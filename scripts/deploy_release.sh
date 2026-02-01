#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(git -C "$ROOT_DIR" rev-parse --show-toplevel)"
DESC=""

log() {
  printf "[%s] %s\n" "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

find_sdk_root() {
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    echo "$ANDROID_SDK_ROOT"
    return 0
  fi
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    echo "$ANDROID_HOME"
    return 0
  fi
  if [[ -f "$ROOT_DIR/local.properties" ]]; then
    local line
    line=$(rg -n "^sdk.dir=" "$ROOT_DIR/local.properties" | head -n1 || true)
    if [[ -n "$line" ]]; then
      echo "${line#*=}"
      return 0
    fi
  fi
  return 1
}

pick_device() {
  local devices
  devices=$(adb devices -l | rg -v "^List of devices" | awk '$2=="device"{print $1}' || true)
  local count
  count=$(printf "%s\n" "$devices" | rg -c "." || true)

  if [[ "$count" -eq 0 ]]; then
    fail "No adb devices found. Connect a device (wireless debugging ok) and retry."
  fi

  if [[ "$count" -gt 1 ]]; then
    if [[ -z "${ANDROID_SERIAL:-}" ]]; then
      echo "Multiple devices detected:" >&2
      printf "%s\n" "$devices" >&2
      fail "Set ANDROID_SERIAL to select a device."
    fi
    echo "$ANDROID_SERIAL"
    return 0
  fi

  echo "$devices"
}

find_aapt() {
  local sdk_root="$1"
  if [[ -z "$sdk_root" ]]; then
    return 1
  fi

  local build_tools_dir="$sdk_root/build-tools"
  if [[ ! -d "$build_tools_dir" ]]; then
    return 1
  fi

  local aapt_path
  aapt_path=$(ls -1 "$build_tools_dir" 2>/dev/null | sort -V | tail -n1 | awk '{print "'$build_tools_dir'/" $0 "/aapt"}')
  if [[ -x "$aapt_path" ]]; then
    echo "$aapt_path"
    return 0
  fi
  return 1
}

find_apk() {
  local apk="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
  if [[ -f "$apk" ]]; then
    echo "$apk"
    return 0
  fi

  local fallback
  fallback=$(find "$ROOT_DIR/app/build/outputs/apk" -type f -name "*release*.apk" -print0 | xargs -0 ls -t 2>/dev/null | head -n1 || true)
  if [[ -n "$fallback" ]]; then
    echo "$fallback"
    return 0
  fi

  return 1
}

parse_apk_manifest() {
  local aapt="$1"
  local apk="$2"
  local pkg
  local activity

  if [[ -x "$aapt" ]]; then
    pkg=$($aapt dump badging "$apk" | rg -o "package: name='[^']+'" | head -n1 | sed "s/package: name='//" | sed "s/'$//")
    activity=$($aapt dump badging "$apk" | rg -o "launchable-activity: name='[^']+'" | head -n1 | sed "s/launchable-activity: name='//" | sed "s/'$//")

    if [[ -n "$pkg" && -n "$activity" ]]; then
      echo "$pkg" "$activity"
      return 0
    fi
  fi

  # Fallback to manifest parsing
  local manifest="$ROOT_DIR/app/src/main/AndroidManifest.xml"
  if [[ -f "$manifest" ]]; then
    pkg=$(rg -n "<manifest" "$manifest" | head -n1 | sed -n "s/.*package=\"\([^\"]*\)\".*/\1/p")
    if [[ -z "$pkg" ]]; then
      pkg=$(rg -n "android:label" "$manifest" | head -n1 | sed -n "s/.*package=\"\([^\"]*\)\".*/\1/p")
    fi

    activity=$(rg -n "<activity" "$manifest" | rg "MAIN" -n -C 3 | rg -o "android:name=\"[^\"]+\"" | head -n1 | sed "s/android:name=\"//" | sed "s/\"//")

    if [[ -n "$pkg" && -n "$activity" ]]; then
      echo "$pkg" "$activity"
      return 0
    fi
  fi

  return 1
}

normalize_activity() {
  local pkg="$1"
  local activity="$2"
  if [[ "$activity" == .* ]]; then
    echo "${pkg}${activity}"
  elif [[ "$activity" == */* ]]; then
    echo "$activity"
  else
    echo "$activity"
  fi
}

run_once() {
  local sdk_root
  sdk_root=$(find_sdk_root || true)

  local serial
  serial=$(pick_device)

  log "Device: $serial"

  log "Building release..."
  (cd "$ROOT_DIR" && ./gradlew :app:assembleRelease)

  local apk
  apk=$(find_apk || true)
  if [[ -z "$apk" ]]; then
    fail "Release APK not found under app/build/outputs/apk."
  fi
  log "APK: $apk"

  local aapt
  aapt=$(find_aapt "$sdk_root" || true)

  local pkg
  local activity
  read -r pkg activity < <(parse_apk_manifest "$aapt" "$apk" || true)
  if [[ -z "${pkg:-}" || -z "${activity:-}" ]]; then
    fail "Unable to determine package or launchable activity."
  fi

  local full_activity
  full_activity=$(normalize_activity "$pkg" "$activity")

  log "Installing APK..."
  echo "adb -s $serial install -r -t \"$apk\""
  adb -s "$serial" install -r -t "$apk"

  log "Waking/unlocking (best-effort)..."
  adb -s "$serial" shell input keyevent KEYCODE_WAKEUP || true
  adb -s "$serial" shell wm dismiss-keyguard || true
  adb -s "$serial" shell input swipe 500 1800 500 300 || true

  log "Launching: $pkg/$full_activity"
  adb -s "$serial" shell am start -n "$pkg/$full_activity"

  if ! git -C "$REPO_ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    fail "Not a git repository; skipping version bump and commit."
  fi

  local new_version
  new_version=$(python3 - <<'PY'
from pathlib import Path
import re
import sys

path = Path("app/build.gradle.kts")
text = path.read_text()
orig = text

vn = re.search(r'versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)"', text)
if not vn:
    print("")
    sys.exit(2)

major, minor, patch = map(int, vn.groups())
new_version = f"{major}.{minor}.{patch + 1}"
text = re.sub(r'versionName\s*=\s*"\d+\.\d+\.\d+"',
              f'versionName = "{new_version}"', text, count=1)

vc = re.search(r'versionCode\s*=\s*(\d+)', text)
if vc:
    new_code = int(vc.group(1)) + 1
    text = re.sub(r'versionCode\s*=\s*\d+', f"versionCode = {new_code}", text, count=1)

if text == orig:
    print("")
    sys.exit(3)

path.write_text(text)
print(new_version)
PY
)

  if [[ -z "$new_version" ]]; then
    fail "Version bump failed (missing or unparseable versionName)."
  fi

  log "Version bumped to $new_version"

  git -C "$REPO_ROOT" status --porcelain
  git -C "$REPO_ROOT" add -A :/
  if git -C "$REPO_ROOT" diff --cached --quiet; then
    log "No changes to commit."
    return 0
  fi

  local msg
  msg="Release v${new_version}: ${DESC}"
  git -C "$REPO_ROOT" diff --cached --stat
  git -C "$REPO_ROOT" commit -m "$msg"
  log "Committed: $msg"
}

usage() {
  echo "Usage: $0 --desc \"short summary\" | -d \"short summary\"" >&2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -d|--desc)
      shift
      if [[ $# -eq 0 ]]; then
        usage
        exit 1
      fi
      DESC="$1"
      ;;
    *)
      usage
      exit 1
      ;;
  esac
  shift
done

if [[ -z "$DESC" ]]; then
  usage
  exit 1
fi

run_once
