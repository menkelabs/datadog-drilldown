# Documentation generator (MkDocs)

Builds a static site from **existing** repo markdown via the [include-markdown](https://github.com/mondeja/mkdocs-include-markdown-plugin) plugin (no duplicate source of truth).

## Quick start

```bash
cd docs/documentation-generator
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
mkdocs serve                # http://127.0.0.1:8000
```

Static output:

```bash
mkdocs build
# → site/  (gitignored)
```

## Configuration

| File | Role |
|------|------|
| [mkdocs.yml](mkdocs.yml) | Site name, theme, nav, plugins |
| [docs/](docs/) | Thin wrapper `.md` files that `{! include !}` canonical paths |
| [requirements.txt](requirements.txt) | Python deps |

## Milestone

Tracked in **[milestones/milestone-3.md](../../milestones/milestone-3.md)** (M3a–M3d: scaffold, curate, CI, publish).

## Troubleshooting

- **Include path errors:** Wrappers live in `docs/`; paths like `../../../README.md` are relative to each wrapper file.
- **`mkdocs build --strict` fails:** Often unclosed fences or duplicate headings in included files; fix upstream markdown or relax `strict` in `mkdocs.yml` temporarily.
