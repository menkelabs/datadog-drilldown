"""SolveRecord — JSON-serializable run summary (contract for Phase 1)."""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from typing import Any, Literal

SolverMode = Literal["local_classical", "leap_hybrid"]
StrategyChoice = Literal["heuristic_only", "qubo"]

# Bump when QUBO mapping or SolveRecord contract changes (Kotlin / JSONL consumers).
DEFAULT_ENCODING_VERSION = "1"


@dataclass
class SolveRecord:
    instance_id: str
    solver_mode: SolverMode
    strategy_choice: StrategyChoice
    strategy_reason: str
    n_vars: int
    objective: float
    selected_decisions: list[str]
    runtime_ms: float
    baseline_objective: float
    vs_baseline_delta: float
    encoding_version: str = DEFAULT_ENCODING_VERSION
    tier: str | None = None

    def to_json_dict(self) -> dict[str, Any]:
        d = asdict(self)
        return d

    def to_json_line(self) -> str:
        return json.dumps(self.to_json_dict(), sort_keys=True)
