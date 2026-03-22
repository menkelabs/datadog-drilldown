# D-Wave Leap API token — obtain and configure

This repo’s Python package **`dice-leap-poc`** can solve QUBOs with **local classical** code paths by default. **D-Wave Leap** is **optional**: it uses D-Wave’s cloud **hybrid** sampler (`LeapHybridSampler`) when you install the `leap` extra and provide credentials.

**Official D-Wave docs:** [Leap authorization (Ocean)](https://docs.dwavequantum.com/en/latest/ocean/leap_authorization.html) · [Leap service overview](https://docs.dwavesys.com/docs/latest/leap.html)

---

## 1. Create a Leap account

1. Open **[Leap](https://cloud.dwavesys.com/leap/)** (D-Wave’s cloud console).
2. Sign up or sign in. Plans and quotas are defined by D-Wave (including free/trial options when available).
3. Ensure you have a **project** selected (or create one) in the Leap UI — solvers and tokens are associated with projects.

---

## 2. Get an API token

You can authenticate in either of these ways (pick one).

### Option A — Copy token from the Leap dashboard (simple)

1. Log in at [cloud.dwavesys.com/leap](https://cloud.dwavesys.com/leap/).
2. Open your **project** / **API token** (wording varies by UI version).
3. Copy the **API token** (SAPI token) shown for that project.

> If your account type does not show a copyable token in the UI, use Option B.

### Option B — `dwave setup` (recommended in Ocean 6.6+)

After installing the Leap stack in your venv (step 3), run:

```bash
dwave setup --auth
```

This walks you through browser login and stores credentials (often under **`~/.dwave/`**). In cloud IDEs where localhost callbacks fail, use:

```bash
dwave setup --oob
```

For a specific project:

```bash
dwave setup --auth --project YOUR_PROJECT_ID
```

See: [Authorizing access to the Leap service](https://docs.dwavequantum.com/en/latest/ocean/leap_authorization.html).

---

## 3. Install Leap dependencies (this repo)

From the **`dice-leap-poc/`** directory, with your venv activated:

```bash
pip install -e ".[dev,leap]"
```

- **`[leap]`** pulls in `dwave-system` (and transitively the Ocean pieces needed for `LeapHybridSampler`).
- Without `[leap]`, choosing `leap_hybrid` will fail with an import/configuration error by design.

---

## 4. Expose the token to the process

### Environment variable (works everywhere, including JVM subprocess)

```bash
export DWAVE_API_TOKEN="your-token-here"
```

The **`embabel-dice-rca`** QUBO bridge runs `dice-leap-poc/scripts/solve_json.py` as a subprocess; it **inherits** the environment of the shell that started the JVM. Set `DWAVE_API_TOKEN` **before** `mvn spring-boot:run` (or your IDE run configuration).

### Config file (Ocean CLI)

If you used `dwave setup`, credentials may live under **`~/.dwave/`**. That directory is **gitignored** in this repo (see root `.gitignore`) — do not commit it.

---

## 5. Quick verification

### A. Ocean connectivity (if CLI installed)

```bash
dwave ping
```

### B. This repo — Leap-marked tests

```bash
cd dice-leap-poc
pytest tests/test_leap.py -m leap -q
```

Requires `DWAVE_API_TOKEN` (or valid `dwave` config) and `pip install -e ".[dev,leap]"`.

### C. One-shot solve to stdout (`leap_hybrid`)

```bash
cd dice-leap-poc
source .venv/bin/activate   # if you use a venv
export DWAVE_API_TOKEN="..."   # if not using dwave config alone
PYTHONPATH=. python scripts/solve_json.py \
  --input sample_data/toy_dw_md.json \
  --strategy-choice qubo \
  --solver-mode leap_hybrid
```

Expect `solver_mode` **`leap_hybrid`** in the printed JSON line. First calls may be slower (cold start, queue).

---

## 6. GitHub Actions (optional)

To run cloud Leap tests in CI, add a repository secret **`DWAVE_API_TOKEN`** and use the optional workflow [`.github/workflows/dice-leap-poc-leap.yml`](../.github/workflows/dice-leap-poc-leap.yml). Default PR checks **do not** require Leap. See [docs/CI_BRANCH_PROTECTION.md](CI_BRANCH_PROTECTION.md).

---

## 7. Security checklist

| Do | Don’t |
|----|--------|
| Keep the token in **env vars**, **password managers**, or **GitHub Secrets** | Commit tokens or paste them into tracked files |
| Rely on **`.gitignore`** for `~/.dwave/` style paths | Share tokens in screenshots or chat logs |
| Follow your org’s **quota / spend** policies | Run large hybrid jobs in CI without limits |

---

## Related docs

- [dice-leap-poc/README.md](../dice-leap-poc/README.md) — install, solver modes, smoke scripts  
- [dwave.md](../dwave.md) — architecture (DICE + QUBO + optional Leap)  
- [docs/DWAVE_REAL_WORLD_METRICS.md](DWAVE_REAL_WORLD_METRICS.md) — pilot metrics  
