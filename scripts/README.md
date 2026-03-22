# Scripts

## `qubo-e2e-smoke.sh`

One-shot setup and smoke test for the **Python ↔ JVM QUBO bridge** (no Datadog or LLM required).

- Creates `dice-leap-poc/.venv` if missing
- `pip install -e ".[dev]"`
- Runs `scripts/solve_json.py` on the toy fixture
- Runs `DiceLeapPythonSolverIntegrationTest` in `embabel-dice-rca` (needs **JDK 21** + **Maven**)
- Exports **`PYTHON`** and **`QUBO_PYTHON_EXECUTABLE`** to `dice-leap-poc/.venv/bin/python` so the JVM test uses the same interpreter as the venv (Maven does not always inherit `activate`’s `PATH`)

```bash
chmod +x scripts/qubo-e2e-smoke.sh   # once
./scripts/qubo-e2e-smoke.sh
```

Options: `--skip-mvn` (Python only), `--no-hints` (omit Spring Boot env exports).

### Supervised / debug runs (for you + assistant)

I can’t attach to your laptop’s terminal live; the practical loop is:

1. **Here (Cursor workspace):** ask to *run supervised smoke* — I run `./scripts/qubo-e2e-smoke.sh --verbose` (or `--log=/path/to/smoke.log`) with full permissions and interpret the full Maven/JUnit output.
2. **On your machine:** run the same command and paste the log (or the `--log=` file) if behavior differs (e.g. JDK path, `~/.m2`, corporate proxy).

`--verbose`: no `mvn -q`, adds `-e` and `-DtrimStackTrace=false`, louder pip.  
`QUBO_SMOKE_VERBOSE=1` also enables verbose mode.  
Example: `./scripts/qubo-e2e-smoke.sh --verbose --log=/tmp/qubo-smoke.log`
