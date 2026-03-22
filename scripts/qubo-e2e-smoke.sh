#!/usr/bin/env bash
# End-to-end QUBO smoke: Python dice-leap-poc + JVM subprocess bridge (no Datadog/LLM required).
#
# Usage:
#   ./scripts/qubo-e2e-smoke.sh              # venv, pip install, toy solve, JVM IT
#   ./scripts/qubo-e2e-smoke.sh --skip-mvn   # Python-only (no JDK/Maven)
#   ./scripts/qubo-e2e-smoke.sh --no-hints   # omit Spring Boot env hints at the end
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POC="$ROOT/dice-leap-poc"
SKIP_MVN=false
NO_HINTS=false

for arg in "$@"; do
  case "$arg" in
    --skip-mvn) SKIP_MVN=true ;;
    --no-hints) NO_HINTS=true ;;
    -h|--help)
      echo "Usage: $0 [--skip-mvn] [--no-hints]"
      exit 0
      ;;
  esac
done

if [[ ! -f "$POC/pyproject.toml" ]]; then
  echo "error: dice-leap-poc not found at $POC" >&2
  exit 1
fi

echo "==> Using dice-leap-poc at $POC"

if [[ ! -d "$POC/.venv" ]]; then
  echo "==> Creating venv in dice-leap-poc/.venv"
  python3 -m venv "$POC/.venv"
fi
# shellcheck source=/dev/null
source "$POC/.venv/bin/activate"

echo "==> pip install -e .[dev] (in dice-leap-poc)"
pip install -q -U pip
( cd "$POC" && pip install -q -e ".[dev]" )

export DICE_LEAP_POC_ROOT="$POC"

echo "==> Python: solve_json.py (toy fixture, qubo + local_classical)"
PYTHONPATH="$POC" python3 "$POC/scripts/solve_json.py" \
  --input "$POC/sample_data/toy_dw_md.json" \
  --strategy-choice qubo

if [[ "$SKIP_MVN" == "true" ]]; then
  echo "==> Skipping Maven (JVM) test (--skip-mvn)"
else
  echo "==> JVM: DiceLeapPythonSolverIntegrationTest (requires mvn + JDK 21)"
  (cd "$ROOT/embabel-dice-rca" && mvn -q test -Dtest=DiceLeapPythonSolverIntegrationTest)
fi

echo ""
echo "OK — QUBO subprocess path works (Python + JVM bridge)."

if [[ "$NO_HINTS" != "true" ]]; then
  echo ""
  echo "Full app (QUBO on in JVM — still need DD_API_KEY / OPENAI_API_KEY etc. for real RCA):"
  echo "  export DICE_LEAP_POC_ROOT=\"$POC\""
  echo "  export EMBABEL_RCA_QUBO_ENABLED=true"
  echo "  export EMBABEL_RCA_QUBO_PYTHON_EXECUTABLE=\"$POC/.venv/bin/python\""
  echo "  cd \"$ROOT/embabel-dice-rca\" && mvn -q spring-boot:run"
fi
