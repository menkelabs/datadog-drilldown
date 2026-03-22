# Datadog Drilldown — video production bundle

This directory is a **complete demo bundle** for generating **narrated MP4 segments** using the same pattern as [`example-demo-bundle`](../example-demo-bundle/) and the portable scripts in [`video-framework`](../video-framework/).

It is aligned with the long-form script in **[`docs/VIDEO_NARRATIVE_ARCHITECTURE_AND_DWAVE.md`](../../VIDEO_NARRATIVE_ARCHITECTURE_AND_DWAVE.md)** (architecture + tests + Test Report UI + D-Wave / QUBO).

## Layout

```text
datadog-drilldown-video-docs/
  README.md                 ← you are here
  compose.sh                ← mux visuals + TTS (VISUAL_MAP: text | vhs | still | manim)
  generate-all.sh           ← TTS → optional VHS → compose
  generate_text_visual.py   ← clean staggered text on solid bg (no terminal recording)
  generate-narration.py     ← OpenAI TTS (product-specific instructions)
  requirements.txt
  narration/                ← NN-topic.md → audio/NN-topic.mp3
  visual-text/              ← one line per row, stem matches narration file (e.g. 01-welcome-scope.txt)
  audio/                    ← generated (gitignored)
  text-rendered/            ← optional cache of per-stem text MP4s (compose writes recordings/*.mp4)
  terminal/*.tape           ← VHS sources (optional if you switch VISUAL_MAP to vhs:…)
  terminal/rendered/*.mp4   ← VHS output (gitignored)
  recordings/               ← per-segment + full-demo.mp4 (gitignored)
```

## Prerequisites

- **Python 3.10+**, **ffmpeg**
- **`OPENAI_API_KEY`** for TTS (or `--skip-tts` if you place MP3s in `audio/` by hand)
- **VHS + ttyd** — only if you set **`VISUAL_MAP`** entries to **`vhs:…`** in `compose.sh`. The default is **`text`** (no terminal screen grabs).

`OPENAI_API_KEY` is loaded from nested `.env` files with `setdefault`, then **repository root** `.env` is applied again with **overwrite** so a key you export in the shell does not hide an updated root `.env`. If TTS still returns **401**, rotate the key at [platform.openai.com](https://platform.openai.com/account/api-keys) — no quotes needed in `.env` (`OPENAI_API_KEY=sk-...`).

## GitHub Pages (CI publish on `main`)

**Live site (after a green deploy):** **[https://menkelabs.github.io/datadog-drilldown/](https://menkelabs.github.io/datadog-drilldown/)**  
**Workflow:** [video-docs-pages.yml](https://github.com/menkelabs/datadog-drilldown/actions/workflows/video-docs-pages.yml)

When you push changes under **`datadog-drilldown-video-docs/`**, **[`.github/workflows/video-docs-pages.yml`](../../../.github/workflows/video-docs-pages.yml)** will:

1. Install **ffmpeg** and Python deps  
2. Run **OpenAI TTS** (`generate-narration.py`) — requires repository secret **`OPENAI_API_KEY`**  
3. Run **`compose.sh`** (default **text** visuals)  
4. Deploy **`pages-site/index.html`** plus **`recordings/*.mp4`** to **GitHub Pages**

**One-time repo setup**

1. **Settings → Secrets and variables → Actions** → create secret **`OPENAI_API_KEY`**.  
2. **Settings → Pages** → **Build and deployment** → **Source: GitHub Actions**.  
3. After the first successful run, open the workflow’s **deploy** job for the public **Page URL** (e.g. `https://<org>.github.io/<repo>/`).

> **Note:** A GitHub repository can only host **one** Pages site. If you already publish another site from this repo, either combine assets into a single workflow/site or use a dedicated repo for videos.

## Quick start

From **this directory**:

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
# Optional Manim: pip install -r requirements-manim.txt

./generate-all.sh --dry-run
./generate-all.sh --skip-vhs   # explicit skip if you have no VHS (default visuals are text anyway)
./generate-all.sh
```

Outputs:

- `audio/*.mp3` — TTS per narration stem  
- `terminal/rendered/*.mp4` — from `.tape` files (if VHS ran)  
- `recordings/<stem>.mp4` — composed segments (default: **text animation** + narration)  
- `recordings/full-demo.mp4` — concat of segments **01–05** when all compose successfully  

## Visual modes (`compose.sh` → `VISUAL_MAP`)

| Mode | Example | Notes |
|------|-----------|--------|
| **`text`** | `VISUAL_MAP[01]="text"` | **Default.** Reads `visual-text/<stem>.txt`, builds 1280×720 with staggered **drawtext** on a solid color. No shell, no prompts, no noisy screenshots. |
| **`vhs`** | `VISUAL_MAP[01]="vhs:01-welcome-scope.mp4"` | Terminal typing from `terminal/rendered/` (requires VHS render step). |
| **`still`** | `VISUAL_MAP[01]="still:1a237e"` | Solid color only. |
| **`manim`** | `VISUAL_MAP[01]="manim:SceneName.mp4"` | Manim output under `animations/media/…` |

Edit **`visual-text/<stem>.txt`** to change on-screen bullets; keep lines reasonably short so they fit at 1280×720.

## Segments (script map)

| Seg | Narration | Visual (default) | Maps to narrative doc |
|-----|-----------|------------------|-------------------------|
| **01** | `01-welcome-scope.md` | Text: `visual-text/01-welcome-scope.txt` | Welcome / DICE+QUBO glue |
| **02** | `02-architecture.md` | Text: `02-architecture.txt` | Four modules + layout |
| **03** | `03-tests-and-ui.md` | Text: `03-tests-and-ui.txt` | CI + Test Report UI |
| **04** | `04-dwave-qubo.md` | Text: `04-dwave-qubo.txt` | Integration + sample_data / rollover |
| **05** | `05-jvm-bridge-smoke.md` | Text: `05-jvm-bridge-smoke.txt` | Smoke script |

**Motion:** Default is **staggered text** (ffmpeg), not terminal capture. For **Manim** or **VHS**, change **`VISUAL_MAP`** and add assets.

## Refreshing framework scripts

If [`video-framework`](../video-framework/) is updated upstream, diff-merge:

- `compose.sh` — keep **`DEFAULT_SEGMENTS`**, **`FULL_CONCAT_SEGMENTS`**, **`VISUAL_MAP`**
- `generate-all.sh` — keep **`ENV_FILE`** path (`../../../.env` → repo root)
- `generate-narration.py` — keep **`INSTRUCTIONS`** and extra `.env` candidates

## Canonical prose script

**`narration/*.md`** should be **spoken copy only** (no segment titles, duration, editor notes, or references to “this video” or “this bundle”). Shot lists and **`[VISUAL]`** cues belong in **[`docs/VIDEO_NARRATIVE_ARCHITECTURE_AND_DWAVE.md`](../../VIDEO_NARRATIVE_ARCHITECTURE_AND_DWAVE.md)** or your editor’s notes — not in these files.
