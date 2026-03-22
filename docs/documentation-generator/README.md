# Documentation generator (extracted assets)

This folder holds a **copy** of the **demo / video generation framework** from `docs/demos/`, packaged so you can **reuse it in other repositories** without moving the canonical implementation out of tekton-dag.

| Path | Purpose |
|------|---------|
| **[video-framework/](video-framework/)** | `compose.sh`, `generate-narration.py`, `generate-all.sh`, `requirements.txt` — drop into your project (e.g. `docs/demos/`). |
| **[example-demo-bundle/](example-demo-bundle/)** | Minimal narration + VHS tape to sanity-check the pipeline. |
| **[datadog-drilldown-video-docs/](datadog-drilldown-video-docs/)** | **This repo’s** five-segment narration + `compose.sh` / `generate-all.sh` aligned with `docs/VIDEO_NARRATIVE_…`. |

**Canonical source** (living copy used by this repo): `docs/demos/` in tekton-dag. When improving the framework, update **both** places or re-copy from `docs/demos/` into `video-framework/` periodically.

**Later:** you can publish `video-framework/` as a shared submodule or package; this layout is the staging area until then.

See [video-framework/README.md](video-framework/README.md) for prerequisites (ffmpeg, VHS, optional Manim, OpenAI TTS) and integration steps.
