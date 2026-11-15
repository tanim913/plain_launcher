#!/usr/bin/env bash
# Ad Shield: reversibly disable MIUI / Realme ad packages over ADB and set Private DNS.
#
#   adshield.sh detect                         show phone + detected brand
#   adshield.sh status                         per-package state (installed / enabled / disabled)
#   adshield.sh apply [--bloat] [--yes]        disable listed packages (dry-run unless --yes)
#   adshield.sh restore [--yes]                undo everything apply did (dry-run unless --yes)
#   adshield.sh dns on|off|status              ad-blocking Private DNS (default dns.adguard-dns.com)
#   adshield.sh grant                          let Plain Launcher change Private DNS by itself
#
# Env: ADB=<path to adb>  SERIAL=<device serial>  DNS_HOST=<private dns hostname>
# Nothing is ever uninstalled. Packages are only disabled for user 0 (`pm disable-user`),
# and every change is written to state/<serial>.log so `restore` can undo exactly that.

set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LISTS="$HERE/lists"
STATE_DIR="$HERE/state"
ADB="${ADB:-$(command -v adb || echo "$HOME/Android/Sdk/platform-tools/adb")}"
DNS_HOST="${DNS_HOST:-dns.adguard-dns.com}"
APP_ID="app.plainlauncher"

die() { echo "error: $*" >&2; exit 1; }
[ -x "$ADB" ] || die "adb not found. Set ADB=/path/to/adb"

# ---- device selection ---------------------------------------------------------------------
pick_device() {
  if [ -n "${SERIAL:-}" ]; then return; fi
  local devs
  devs="$("$ADB" devices | awk 'NR>1 && $2=="device"{print $1}')"
  local n; n=$(printf '%s\n' "$devs" | grep -c .)
  [ "$n" -eq 1 ] || die "need exactly one authorized device, found $n. Check 'adb devices' or set SERIAL=..."
  SERIAL="$devs"
}
sh_() { "$ADB" -s "$SERIAL" shell "$@" </dev/null | tr -d '\r'; }
prop() { sh_ getprop "$1"; }

detect_brand() {
  if [ -n "$(prop ro.miui.ui.version.name)" ]; then echo miui; return; fi
  if [ -n "$(prop ro.build.version.realmeui)" ] || [ -n "$(prop ro.build.version.opporom)" ] \
     || [ "$(prop ro.product.brand | tr 'A-Z' 'a-z')" = "realme" ]; then echo realme; return; fi
  echo unknown
}

# ---- lists --------------------------------------------------------------------------------
# prints "<tier> <package>" lines from a list file (comments stripped)
read_list() { sed -e 's/#.*//' -e 's/[[:space:]]\+/ /g' -e 's/^ //' -e 's/ $//' "$1" | awk 'NF>=2{print $1, $2}'; }

is_protected() {
  local pkg="$1" pat
  while read -r pat; do
    [ -z "$pat" ] && continue
    case "$pat" in
      *\*) [[ "$pkg" == ${pat} ]] && return 0 ;;
      *)   [ "$pkg" = "$pat" ] && return 0 ;;
    esac
  done < <(sed -e 's/#.*//' -e 's/[[:space:]]//g' "$LISTS/protected.txt")
  return 1
}

# ---- device package state -----------------------------------------------------------------
load_state() {
  INSTALLED="$(sh_ pm list packages | sed 's/^package://')"
  DISABLED="$(sh_ pm list packages -d | sed 's/^package://')"
}
has() { printf '%s\n' "$1" | grep -qxF -- "$2"; }

pkg_state() {
  if ! has "$INSTALLED" "$1"; then echo "not-installed"
  elif has "$DISABLED" "$1"; then echo "disabled"
  else echo "enabled"; fi
}

state_file() { echo "$STATE_DIR/$SERIAL.log"; }

# ---- commands -----------------------------------------------------------------------------
cmd_detect() {
  echo "serial:  $SERIAL"
  echo "model:   $(prop ro.product.model)"
  echo "android: $(prop ro.build.version.release) (sdk $(prop ro.build.version.sdk))"
  echo "miui:    $(prop ro.miui.ui.version.name) $(prop ro.build.version.incremental)"
  echo "brand:   $(detect_brand)"
}

list_file_for_brand() {
  case "$(detect_brand)" in
    miui)   echo "$LISTS/miui.txt" ;;
    realme) echo "$LISTS/realme.txt" ;;
    *)      die "unrecognised phone brand; only MIUI and Realme UI lists exist" ;;
  esac
}

cmd_status() {
  local f; f="$(list_file_for_brand)"; load_state
  printf '%-7s %-45s %s\n' TIER PACKAGE STATE
  while read -r tier pkg; do
    local st; st="$(pkg_state "$pkg")"
    is_protected "$pkg" && st="PROTECTED"
    printf '%-7s %-45s %s\n' "$tier" "$pkg" "$st"
  done < <(read_list "$f")
}

cmd_apply() {
  local yes=0 bloat=0 a
  for a in "$@"; do case "$a" in --yes) yes=1 ;; --bloat) bloat=1 ;; *) die "unknown option $a" ;; esac; done
  local f; f="$(list_file_for_brand)"; load_state
  mkdir -p "$STATE_DIR"
  [ $yes -eq 1 ] || echo "DRY RUN: nothing will change. Add --yes to apply."
  printf '%-7s %-45s %s\n' TIER PACKAGE ACTION
  local done_n=0 fail_n=0
  while read -r tier pkg; do
    local action
    if [ "$tier" = "bloat" ] && [ $bloat -eq 0 ]; then action="skip (bloat: use --bloat)"
    elif is_protected "$pkg"; then action="skip (protected)"
    else
      case "$(pkg_state "$pkg")" in
        not-installed) action="skip (not installed)" ;;
        disabled)      action="skip (already disabled; left as you had it)" ;;
        enabled)       action="DISABLE" ;;
      esac
    fi
    printf '%-7s %-45s %s\n' "$tier" "$pkg" "$action"
    if [ "$action" = "DISABLE" ] && [ $yes -eq 1 ]; then
      local out; out="$(sh_ pm disable-user --user 0 "$pkg" 2>&1)"
      if printf '%s' "$out" | grep -q 'new state: disabled'; then
        echo "$(date +%s) disable $pkg" >> "$(state_file)"; done_n=$((done_n+1))
      else
        echo "         -> FAILED: $out"; fail_n=$((fail_n+1))
      fi
    fi
  done < <(read_list "$f")
  if [ $yes -eq 1 ]; then echo; echo "disabled: $done_n   failed: $fail_n   log: $(state_file)"; fi
}

cmd_restore() {
  local yes=0 a
  for a in "$@"; do case "$a" in --yes) yes=1 ;; *) die "unknown option $a" ;; esac; done
  local log; log="$(state_file)"
  [ -s "$log" ] || { echo "nothing to restore for $SERIAL"; return; }
  [ $yes -eq 1 ] || echo "DRY RUN: nothing will change. Add --yes to restore."
  local ok=0 bad=0
  while read -r _ts verb pkg; do
    [ "$verb" = "disable" ] || continue
    echo "ENABLE $pkg"
    if [ $yes -eq 1 ]; then
      if sh_ pm enable --user 0 "$pkg" 2>&1 | grep -q 'new state: enabled'; then ok=$((ok+1)); else bad=$((bad+1)); echo "  -> FAILED"; fi
    fi
  done < <(tac "$log")
  if [ $yes -eq 1 ]; then
    echo "re-enabled: $ok   failed: $bad"
    [ $bad -eq 0 ] && mv "$log" "$log.restored.$(date +%s)"
  fi
}

cmd_dns() {
  local mode_file="$STATE_DIR/$SERIAL.dns"
  case "${1:-status}" in
    status)
      echo "private_dns_mode:      $(sh_ settings get global private_dns_mode)"
      echo "private_dns_specifier: $(sh_ settings get global private_dns_specifier)" ;;
    on)
      mkdir -p "$STATE_DIR"
      if [ ! -f "$mode_file" ]; then
        printf '%s\n%s\n' "$(sh_ settings get global private_dns_mode)" "$(sh_ settings get global private_dns_specifier)" > "$mode_file"
      fi
      sh_ settings put global private_dns_specifier "$DNS_HOST"
      sh_ settings put global private_dns_mode hostname
      echo "Private DNS -> $DNS_HOST (previous values saved)"; cmd_dns status ;;
    off)
      local pm="opportunistic" ps="null"
      if [ -f "$mode_file" ]; then pm="$(sed -n 1p "$mode_file")"; ps="$(sed -n 2p "$mode_file")"; fi
      [ -z "$pm" ] || [ "$pm" = "null" ] && pm="opportunistic"
      sh_ settings put global private_dns_mode "$pm"
      if [ -n "$ps" ] && [ "$ps" != "null" ]; then sh_ settings put global private_dns_specifier "$ps"
      else sh_ settings delete global private_dns_specifier >/dev/null; fi
      rm -f "$mode_file"
      echo "Private DNS restored"; cmd_dns status ;;
    *) die "usage: dns on|off|status" ;;
  esac
}

cmd_grant() {
  sh_ pm grant "$APP_ID" android.permission.WRITE_SECURE_SETTINGS \
    && echo "granted WRITE_SECURE_SETTINGS to $APP_ID"
}

usage() { sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; }

main() {
  local cmd="${1:-}"; shift || true
  case "$cmd" in
    ""|-h|--help|help) usage; exit 0 ;;
  esac
  pick_device
  case "$cmd" in
    detect)  cmd_detect ;;
    status)  cmd_status ;;
    apply)   cmd_apply "$@" ;;
    restore) cmd_restore "$@" ;;
    dns)     cmd_dns "$@" ;;
    grant)   cmd_grant ;;
    *) usage; exit 2 ;;
  esac
}
main "$@"
