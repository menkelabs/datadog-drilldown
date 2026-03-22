# Documentation generator

Templates and the **Datadog Drilldown** video bundle. **[GitHub Pages](https://menkelabs.github.io/datadog-drilldown/)** deploys **pre-built** `recordings/*.mp4` from git (no video generation in CI).

| Path | Purpose |
|------|---------|
| [`video-framework/`](video-framework/) | Portable scripts: `compose.sh`, `generate-narration.py`, `generate-all.sh`, `requirements.txt` — copy into other repos. |
| [`example-demo-bundle/`](example-demo-bundle/) | Minimal narration + tape to sanity-check the pipeline. |
| [`datadog-drilldown-video-docs/`](datadog-drilldown-video-docs/) | This repo’s five-segment narration, text visuals, `compose.sh`, and Pages `index.html`. |

See [`video-framework/README.md`](video-framework/README.md) for prerequisites (ffmpeg, optional VHS/Manim, OpenAI TTS).

**MkDocs:** There is no `mkdocs.yml` in this folder right now. The **`docs-site`** GitHub Action installs `requirements.txt` here and runs MkDocs **only if** you add `mkdocs.yml` later.
