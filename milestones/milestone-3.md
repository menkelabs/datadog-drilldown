# Milestone 3 — Project documentation site (MkDocs)

**Status:** M3a + M3c (verify) in place; M3b curation + M3d publishing optional  
**Builds on:** [Milestone 2](milestone-2.md) (operational docs, ADRs, metrics)  
**Location:** [docs/documentation-generator/](../docs/documentation-generator/)

## Purpose

Produce a **browsable documentation site** from existing markdown (root README, `docs/`, `milestones/`, `dwave.md`) without moving canonical sources. The site **includes** those files at build time so a single edit in-repo updates both GitHub and the generated HTML.

## Phased delivery

| Phase | Name | Intent | Exit criteria |
|-------|------|--------|----------------|
| **M3a** | **Scaffold** | MkDocs + Material + include-markdown; local `mkdocs serve` / `build`. | `mkdocs build` succeeds from `docs/documentation-generator/`; `site/` output (gitignored). |
| **M3b** | **Curate nav** | Tabs/sections for D-Wave, milestones, ops; add pages as needed (e.g. dice-leap-poc README). | Nav covers README, dwave, Leap setup, QUBO docs, ADR 0002, M1–M3, CI doc. |
| **M3c** | **CI verify** | Workflow runs `mkdocs build --strict` on PR when generator or included paths change. | Green job; fails on broken includes or MD errors. ([`docs-site.yml`](../.github/workflows/docs-site.yml)) |
| **M3d** | **Publish** (optional) | Deploy `site/` to GitHub Pages or static host. | URL documented; branch or `workflow_dispatch` policy agreed. |

## Work items (checklist)

### M3a — Scaffold

- [x] **G1** — `requirements.txt` (mkdocs, material, include-markdown-plugin).
- [x] **G2** — `mkdocs.yml` (theme, plugins, nav, strict).
- [x] **G3** — Wrapper pages under `docs/documentation-generator/docs/*.md` with `{! path !}` includes.
- [x] **G4** — `README.md` in generator folder (install, serve, build, paths).
- [x] **G5** — `.gitignore` for `.venv/`, `site/`.

### M3b — Curate

- [ ] **G6** — Add module READMEs to nav (e.g. `embabel-dice-rca`, `dice-leap-poc`, `test-report-server`) via include wrappers.
- [ ] **G7** — Fix relative links inside included pages when viewed on site (or document “open on GitHub” for deep links).
- [ ] **G8** — Optional: Mermaid / PlantUML render (plugin) for diagrams referenced from DIAGRAMS.md.

### M3c — CI

- [x] **G9** — [.github/workflows/docs-site.yml](../.github/workflows/docs-site.yml) (Python 3.11+, pip install, `mkdocs build --strict`, path filters).
- [ ] **G10** — Document job in [docs/CI_BRANCH_PROTECTION.md](../docs/CI_BRANCH_PROTECTION.md) (optional required check).

### M3d — Publish

- [ ] **G11** — GitHub Pages workflow or manual upload playbook.
- [ ] **G12** — Root [README.md](../README.md) link to published site (when URL exists).

## Non-goals (unless added)

- Replacing canonical markdown locations (everything stays in tree; generator only aggregates).
- Auto-extracting Kotlindoc into MkDocs (separate effort).

## References

- [MkDocs](https://www.mkdocs.org/)
- [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/)
- [mkdocs-include-markdown-plugin](https://github.com/mondeja/mkdocs-include-markdown-plugin)

---

*Last updated: 2026-03-22*
