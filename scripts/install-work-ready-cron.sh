#!/usr/bin/env bash

set -euo pipefail

readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly MARKER="# liano-work-ready-issue"
readonly CRON_LINE="0 10 * * * PATH=/home/codex/.local/bin:/usr/local/bin:/usr/bin:/bin $PROJECT_ROOT/scripts/work-ready-issue.sh >> /tmp/liano-work-ready-issue.log 2>&1 $MARKER"

case "${1:-}" in
    "")
        (crontab -l 2>/dev/null || true) | { grep -Fv "$MARKER" || true; } >"${TMPDIR:-/tmp}/liano-crontab.$$.tmp"
        printf '%s\n' "$CRON_LINE" >>"${TMPDIR:-/tmp}/liano-crontab.$$.tmp"
        crontab "${TMPDIR:-/tmp}/liano-crontab.$$.tmp"
        rm -f "${TMPDIR:-/tmp}/liano-crontab.$$.tmp"
        echo "Installed daily Liano work-ready issue job for 10:00 UTC."
        ;;
    --uninstall)
        (crontab -l 2>/dev/null || true) | { grep -Fv "$MARKER" || true; } | crontab -
        echo "Removed the daily Liano work-ready issue job."
        ;;
    *)
        echo "Usage: $0 [--uninstall]" >&2
        exit 64
        ;;
esac
