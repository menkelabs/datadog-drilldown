# Scripts

## `qubo-e2e-smoke.sh`

One-shot setup and smoke test for the **Python ↔ JVM QUBO bridge** (no Datadog or LLM required).

- Creates `dice-leap-poc/.venv` if missing
- `pip install -e ".[dev]"`
- Runs `scripts/solve_json.py` on the toy fixture
- Runs `DiceLeapPythonSolverIntegrationTest` in `embabel-dice-rca` (needs **JDK 21** + **Maven**)

```bash
chmod +x scripts/qubo-e2e-smoke.sh   # once
./scripts/qubo-e2e-smoke.sh
```

Options: `--skip-mvn` (Python only), `--no-hints` (omit Spring Boot env exports).
