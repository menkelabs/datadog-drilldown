"""End-to-end: instance → BQM → local or Leap solve → SolveRecord."""

from __future__ import annotations

from pathlib import Path
from typing import Literal

from dice_leap_poc.baseline import greedy_min_energy
from dice_leap_poc.instance import Instance
from dice_leap_poc.qubo import assignment_to_selected, build_bqm
from dice_leap_poc.record import DEFAULT_ENCODING_VERSION, SolveRecord
from dice_leap_poc.solve_leap import solve_leap_hybrid
from dice_leap_poc.solve_local import solve_local_sa
from dice_leap_poc.strategy import RolloverConfig, plan_strategy

SolverMode = Literal["local_classical", "leap_hybrid"]


def run_instance(
    inst: Instance,
    *,
    strategy_choice: str | None = None,
    strategy_reason: str | None = None,
    rollover: RolloverConfig | None = None,
    solver_mode: SolverMode = "local_classical",
    num_reads: int = 2000,
    seed: int | None = 42,
    leap_time_limit_s: float | None = None,
) -> SolveRecord:
    if strategy_choice is None:
        strategy_choice, auto_reason = plan_strategy(inst, cfg=rollover)
        strategy_reason = auto_reason
    elif strategy_reason is None:
        strategy_reason = f"explicit_{strategy_choice}"

    bqm = build_bqm(inst)
    heuristic_assign = greedy_min_energy(inst, bqm)
    e_heur = bqm.energy(heuristic_assign)
    encoding_version = inst.encoding_version or DEFAULT_ENCODING_VERSION

    if strategy_choice == "heuristic_only":
        sel = assignment_to_selected(bqm, heuristic_assign)
        return SolveRecord(
            instance_id=inst.instance_id,
            solver_mode="local_classical",
            strategy_choice="heuristic_only",
            strategy_reason=strategy_reason,
            n_vars=len(bqm.variables),
            objective=e_heur,
            selected_decisions=sel,
            runtime_ms=0.0,
            baseline_objective=e_heur,
            vs_baseline_delta=0.0,
            encoding_version=encoding_version,
            tier=inst.tier,
        )

    if solver_mode == "leap_hybrid":
        sample, e_best, rt = solve_leap_hybrid(bqm, time_limit_s=leap_time_limit_s)
        record_mode: SolverMode = "leap_hybrid"
    elif solver_mode == "local_classical":
        sample, e_best, rt = solve_local_sa(bqm, num_reads=num_reads, seed=seed)
        record_mode = "local_classical"
    else:
        raise ValueError(f"Unknown solver_mode: {solver_mode!r}")

    sel = assignment_to_selected(bqm, sample)
    delta = e_heur - e_best
    return SolveRecord(
        instance_id=inst.instance_id,
        solver_mode=record_mode,
        strategy_choice="qubo",
        strategy_reason=strategy_reason,
        n_vars=len(bqm.variables),
        objective=e_best,
        selected_decisions=sel,
        runtime_ms=rt,
        baseline_objective=e_heur,
        vs_baseline_delta=delta,
        encoding_version=encoding_version,
        tier=inst.tier,
    )


def run_json_file(path: Path | str, **kwargs) -> SolveRecord:
    return run_instance(Instance.load_json(path), **kwargs)


def write_jsonl(record: SolveRecord, out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("a", encoding="utf-8") as f:
        f.write(record.to_json_line() + "\n")
