# Datadog Drilldown — documentation site

This site is **generated** from the repository with [MkDocs](https://www.mkdocs.org/) and the [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/) theme.

**Build locally** (from `docs/documentation-generator/`):

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
mkdocs serve   # http://127.0.0.1:8000
# or: mkdocs build  → output in site/
```

Use the navigation tabs for **included** copies of key markdown (sourced from the repo root and `docs/`). The canonical files remain in Git; this site **includes** them at build time.

See **Milestone 3** in the nav for the roadmap task that owns this generator.
