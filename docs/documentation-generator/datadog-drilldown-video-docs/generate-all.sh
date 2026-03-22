#!/usr/bin/env bash
# generate-all.sh — TTS → optional Manim → VHS → compose (Datadog Drilldown bundle).
#
# Usage:
#   cd docs/documentation-generator/datadog-drilldown-video-docs
#   ./generate-all.sh
#   ./generate-all.sh --skip-tts --skip-manim --skip-vhs
#   ./generate-all.sh --dry-run
#
set -euo pipefail

export PATH="${HOME}/.local/bin:${HOME}/go/bin:${PATH}"

DEMOS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV="${DEMOS_VENV:-$DEMOS_DIR/.venv}"
VHS_BIN="${VHS_BIN:-$(command -v vhs 2>/dev/null || echo "$HOME/go/bin/vhs")}"

MANIM_SCENES="${MANIM_SCENES:-}"

SKIP_TTS=false
SKIP_MANIM=false
SKIP_VHS=false
DRY_RUN=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-tts)   SKIP_TTS=true; shift ;;
        --skip-manim) SKIP_MANIM=true; shift ;;
        --skip-vhs)   SKIP_VHS=true; shift ;;
        --dry-run)    DRY_RUN=true; shift ;;
        *)            echo "Unknown option: $1" >&2; exit 1 ;;
    esac
done

info() { echo "==> $*"; }
step() { echo ""; echo "──────────────────────────────────────────"; echo "  STEP: $*"; echo "──────────────────────────────────────────"; }

if [[ -d "$VENV" ]]; then
    # shellcheck disable=SC1091
    source "$VENV/bin/activate"
else
    echo "WARN: venv not found at $VENV — using current python" >&2
fi

for cmd in python ffmpeg; do
    command -v "$cmd" >/dev/null 2>&1 || { echo "ERROR: $cmd not found" >&2; exit 1; }
done
# VHS optional: default compose.sh uses text visuals (ffmpeg drawtext). Install vhs only if VISUAL_MAP uses vhs:…
if [[ "$SKIP_VHS" != "true" ]]; then
    if ! command -v vhs >/dev/null 2>&1 && [[ ! -x "$VHS_BIN" ]]; then
        echo "WARN: vhs not found — skipping VHS tapes (OK for default text visuals)" >&2
        SKIP_VHS=true
    fi
fi

# Repo root: .../datadog-drilldown-video-docs → ../../../.env
ENV_FILE="${DEMOS_ENV_FILE:-$DEMOS_DIR/../../../.env}"
if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
fi

echo "╔══════════════════════════════════════════════╗"
echo "║  Datadog Drilldown — demo video generator    ║"
echo "╚══════════════════════════════════════════════╝"
info "Demos dir: $DEMOS_DIR"
info "VHS: $VHS_BIN"
info "Manim scenes: ${MANIM_SCENES:-<none>}"

if [[ "$DRY_RUN" == "true" ]]; then
    echo "Planned: TTS, Manim (if MANIM_SCENES set), VHS (if installed / not skipped), compose.sh (default text visuals)"
    exit 0
fi

if [[ "$SKIP_TTS" != "true" ]]; then
    step "TTS narration"
    python "$DEMOS_DIR/generate-narration.py"
else
    step "Skip TTS"
fi

if [[ "$SKIP_MANIM" != "true" ]] && [[ -n "$MANIM_SCENES" ]] && [[ -f "$DEMOS_DIR/animations/scenes.py" ]]; then
    step "Manim"
    cd "$DEMOS_DIR/animations"
    for scene in $MANIM_SCENES; do
        info "manim -qm scenes.py $scene"
        manim -qm scenes.py "$scene"
    done
    cd "$DEMOS_DIR"
elif [[ "$SKIP_MANIM" != "true" ]] && [[ -n "$MANIM_SCENES" ]]; then
    echo "WARN: MANIM_SCENES set but animations/scenes.py missing — skip Manim" >&2
fi

if [[ "$SKIP_VHS" != "true" ]]; then
    step "VHS"
    mkdir -p "$DEMOS_DIR/terminal/rendered"
    shopt -s nullglob
    for tape in "$DEMOS_DIR"/terminal/*.tape; do
        info "$(basename "$tape")"
        "$VHS_BIN" "$tape"
    done
    shopt -u nullglob
else
    step "Skip VHS"
fi

step "Compose (compose.sh default segments)"
bash "$DEMOS_DIR/compose.sh"

echo ""
echo "=== Generation complete ==="
